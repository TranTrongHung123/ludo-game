package vn.ptit.ltm.client.service;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.client.config.ClientConfig;
import vn.ptit.ltm.client.network.ClientRequestException;
import vn.ptit.ltm.client.state.ClientSessionState;
import vn.ptit.ltm.client.state.ConnectionState;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.common.enums.MatchParticipantStatus;
import vn.ptit.ltm.common.enums.PieceColor;
import vn.ptit.ltm.common.enums.RoomState;
import vn.ptit.ltm.common.enums.TurnState;
import vn.ptit.ltm.common.enums.PieceState;
import vn.ptit.ltm.server.network.AuthMessageHandler;
import vn.ptit.ltm.server.network.CompositeConnectionListener;
import vn.ptit.ltm.server.network.HeartbeatManager;
import vn.ptit.ltm.server.network.TcpServer;
import vn.ptit.ltm.server.network.ConnectionRegistry;
import vn.ptit.ltm.server.lobby.LobbyService;
import vn.ptit.ltm.server.repository.UserAccountRecord;
import vn.ptit.ltm.server.repository.UserRepository;
import vn.ptit.ltm.server.repository.CompletedMatchRecord;
import vn.ptit.ltm.server.repository.MatchRepository;
import vn.ptit.ltm.server.repository.PersistedMatchResult;
import vn.ptit.ltm.server.repository.PersistedPlayerResult;
import vn.ptit.ltm.common.dto.ranking.MatchHistoryEntryDto;
import vn.ptit.ltm.common.dto.ranking.RankingEntryDto;
import vn.ptit.ltm.server.room.RoomService;
import vn.ptit.ltm.server.game.DiceRoller;
import vn.ptit.ltm.server.service.AuthService;
import vn.ptit.ltm.server.service.PasswordHasher;
import vn.ptit.ltm.server.service.MatchService;
import vn.ptit.ltm.server.service.RankingService;
import vn.ptit.ltm.server.session.SessionConnectionListener;
import vn.ptit.ltm.server.session.SessionManager;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthClientServiceIntegrationTest {
    @Test
    void registerLoginAndLogoutUseTheRealTcpServerContract() throws Exception {
        InMemoryUserRepository users = new InMemoryUserRepository();
        try (RunningAuthServer server = new RunningAuthServer(users);
             AuthClientService client = new AuthClientService(
                     new ClientConfig("127.0.0.1", server.port()),
                     server.clientSessionState()
             )) {
            client.connectAsync().get(3, TimeUnit.SECONDS);
            assertEquals(ConnectionState.CONNECTED, client.connectionState());

            var registration = client.register("alice", "secret", "Alice")
                    .get(3, TimeUnit.SECONDS);
            assertEquals("alice", registration.profile().username());
            assertEquals("hash:secret", users.findByUsername("alice").orElseThrow().passwordHash());

            var login = client.login("alice", "secret").get(3, TimeUnit.SECONDS);
            assertEquals("Alice", login.profile().displayName());
            assertTrue(server.clientSessionState().isAuthenticated());
            assertEquals(1, server.sessions().activeSessionCount());

            client.logout().get(3, TimeUnit.SECONDS);
            assertFalse(server.clientSessionState().isAuthenticated());
            assertEquals(0, server.sessions().activeSessionCount());
        }
    }

    @Test
    void mapsServerAuthenticationErrorsToTypedClientFailures() throws Exception {
        InMemoryUserRepository users = new InMemoryUserRepository();
        users.create("alice", "hash:secret", "Alice");
        try (RunningAuthServer server = new RunningAuthServer(users);
             AuthClientService client = new AuthClientService(
                     new ClientConfig("127.0.0.1", server.port()),
                     server.clientSessionState()
             )) {
            client.connectAsync().get(3, TimeUnit.SECONDS);

            CompletionException failure = assertThrows(
                    CompletionException.class,
                    () -> client.login("alice", "wrong").join()
            );
            ClientRequestException requestFailure = assertInstanceOf(
                    ClientRequestException.class,
                    failure.getCause()
            );
            assertEquals(ErrorCode.INVALID_CREDENTIALS, requestFailure.errorCode());
            assertFalse(server.clientSessionState().isAuthenticated());
        }
    }

    @Test
    void roomLifecycleAndUnsolicitedRoomUpdatesWorkAcrossTwoClients() throws Exception {
        InMemoryUserRepository users = new InMemoryUserRepository();
        ClientSessionState aliceState = new ClientSessionState();
        ClientSessionState bobState = new ClientSessionState();
        try (RunningAuthServer server = new RunningAuthServer(users);
             AuthClientService alice = new AuthClientService(
                     new ClientConfig("127.0.0.1", server.port()),
                     aliceState
             );
             AuthClientService bob = new AuthClientService(
                     new ClientConfig("127.0.0.1", server.port()),
                     bobState
             )) {
            alice.connectAsync().get(3, TimeUnit.SECONDS);
            bob.connectAsync().get(3, TimeUnit.SECONDS);
            alice.register("alice", "secret", "Alice").get(3, TimeUnit.SECONDS);
            bob.register("bob", "secret", "Bob").get(3, TimeUnit.SECONDS);
            alice.login("alice", "secret").get(3, TimeUnit.SECONDS);
            bob.login("bob", "secret").get(3, TimeUnit.SECONDS);

            assertEquals(2, alice.getOnlinePlayers().get(3, TimeUnit.SECONDS).players().size());
            var created = alice.createRoom().get(3, TimeUnit.SECONDS).room();
            assertEquals(0, created.players().getFirst().slotIndex());

            var joined = bob.joinRoom(created.roomId()).get(3, TimeUnit.SECONDS).room();
            assertEquals(2, joined.players().size());
            assertEquals(1, joined.players().get(1).slotIndex());
            await(() -> aliceState.room().map(room -> room.players().size() == 2).orElse(false));

            bob.leaveRoom(created.roomId()).get(3, TimeUnit.SECONDS);
            assertTrue(bobState.room().isEmpty());
            await(() -> aliceState.room().map(room -> room.players().size() == 1).orElse(false));
            assertEquals("Alice", aliceState.room().orElseThrow().players().getFirst().displayName());
        }
    }

    @Test
    void invitationReadyAndStartGameWorkEndToEndAcrossTwoClients() throws Exception {
        InMemoryUserRepository users = new InMemoryUserRepository();
        ClientSessionState aliceState = new ClientSessionState();
        ClientSessionState bobState = new ClientSessionState();
        try (RunningAuthServer server = new RunningAuthServer(users);
             AuthClientService alice = new AuthClientService(
                     new ClientConfig("127.0.0.1", server.port()),
                     aliceState
             );
             AuthClientService bob = new AuthClientService(
                     new ClientConfig("127.0.0.1", server.port()),
                     bobState
             )) {
            alice.connectAsync().get(3, TimeUnit.SECONDS);
            bob.connectAsync().get(3, TimeUnit.SECONDS);
            alice.register("alice", "secret", "Alice").get(3, TimeUnit.SECONDS);
            bob.register("bob", "secret", "Bob").get(3, TimeUnit.SECONDS);
            alice.login("alice", "secret").get(3, TimeUnit.SECONDS);
            bob.login("bob", "secret").get(3, TimeUnit.SECONDS);

            var room = alice.createRoom().get(3, TimeUnit.SECONDS).room();
            String bobPlayerId = bobState.profile().orElseThrow().playerId();
            alice.invitePlayer(room.roomId(), bobPlayerId).get(3, TimeUnit.SECONDS);
            await(() -> bobState.invitation().isPresent());
            String rejectedInvitationId = bobState.invitation().orElseThrow().invitationId();
            bob.rejectInvitation(rejectedInvitationId).get(3, TimeUnit.SECONDS);
            assertTrue(bobState.invitation().isEmpty());

            alice.invitePlayer(room.roomId(), bobPlayerId).get(3, TimeUnit.SECONDS);
            await(() -> bobState.invitation().isPresent());
            bob.acceptInvitation(bobState.invitation().orElseThrow().invitationId())
                    .get(3, TimeUnit.SECONDS);
            await(() -> aliceState.room().map(value -> value.players().size() == 2).orElse(false));

            CompletionException nonHostFailure = assertThrows(
                    CompletionException.class,
                    () -> bob.startGame(room.roomId()).join()
            );
            ClientRequestException nonHost = assertInstanceOf(
                    ClientRequestException.class,
                    nonHostFailure.getCause()
            );
            assertEquals(ErrorCode.NOT_ROOM_HOST, nonHost.errorCode());

            alice.setReady(room.roomId(), true).get(3, TimeUnit.SECONDS);
            bob.setReady(room.roomId(), true).get(3, TimeUnit.SECONDS);
            await(() -> aliceState.room()
                    .map(value -> value.players().stream().allMatch(player -> player.ready()))
                    .orElse(false));

            var game = alice.startGame(room.roomId()).get(3, TimeUnit.SECONDS);
            assertEquals(RoomState.PLAYING, game.roomState());
            assertEquals(TurnState.WAITING_FOR_ROLL, game.turnState());
            assertEquals("1", game.currentPlayerId());
            assertEquals(4, game.participants().getFirst().pieces().size());
            await(() -> bobState.gameState().isPresent());
            assertEquals(game.matchId(), bobState.gameState().orElseThrow().matchId());
            assertEquals(RoomState.PLAYING, bobState.gameState().orElseThrow().roomState());

            CompletionException wrongTurnFailure = assertThrows(
                    CompletionException.class,
                    () -> bob.rollDice(room.roomId()).join()
            );
            ClientRequestException wrongTurn = assertInstanceOf(
                    ClientRequestException.class,
                    wrongTurnFailure.getCause()
            );
            assertEquals(ErrorCode.NOT_YOUR_TURN, wrongTurn.errorCode());

            var six = alice.rollDice(room.roomId()).get(3, TimeUnit.SECONDS);
            assertEquals(6, six.diceValue());
            assertEquals(4, six.validPieceIds().size());
            await(() -> bobState.gameState()
                    .map(value -> value.turnState() == TurnState.WAITING_FOR_MOVE
                            && Integer.valueOf(6).equals(value.diceValue()))
                    .orElse(false));

            String pieceId = six.validPieceIds().getFirst();
            var spawn = alice.movePiece(room.roomId(), pieceId).get(3, TimeUnit.SECONDS);
            assertEquals(PieceState.ON_TRACK, spawn.piece().state());
            assertEquals(0, spawn.piece().stepCount());
            assertTrue(spawn.bonusRoll());
            assertEquals("1", spawn.gameState().currentPlayerId());
            assertEquals(TurnState.WAITING_FOR_ROLL, spawn.gameState().turnState());

            var three = alice.rollDice(room.roomId()).get(3, TimeUnit.SECONDS);
            assertEquals(3, three.diceValue());
            assertEquals(java.util.List.of(pieceId), three.validPieceIds());
            var advanced = alice.movePiece(room.roomId(), pieceId).get(3, TimeUnit.SECONDS);
            assertEquals(3, advanced.piece().stepCount());
            assertFalse(advanced.bonusRoll());
            assertEquals("2", advanced.gameState().currentPlayerId());
            assertEquals(TurnState.WAITING_FOR_ROLL, advanced.gameState().turnState());
            await(() -> bobState.gameState()
                    .map(value -> "2".equals(value.currentPlayerId())
                            && value.stateVersion() == advanced.gameState().stateVersion())
                    .orElse(false));
        }
    }

    @Test
    void rankingAndMatchHistoryUseAuthenticatedTcpRequests() throws Exception {
        InMemoryUserRepository users = new InMemoryUserRepository();
        try (RunningAuthServer server = new RunningAuthServer(users);
             AuthClientService client = new AuthClientService(
                     new ClientConfig("127.0.0.1", server.port()),
                     server.clientSessionState()
             )) {
            client.connectAsync().get(3, TimeUnit.SECONDS);
            client.register("alice", "secret", "Alice").get(3, TimeUnit.SECONDS);
            client.login("alice", "secret").get(3, TimeUnit.SECONDS);

            var ranking = client.getRanking().get(3, TimeUnit.SECONDS);
            var history = client.getMatchHistory().get(3, TimeUnit.SECONDS);

            assertEquals("1", ranking.entries().getFirst().playerId());
            assertEquals(0, new BigDecimal("3.0").compareTo(ranking.entries().getFirst().totalScore()));
            assertEquals("stored-match", history.matches().getFirst().matchId());
            assertEquals(1, history.matches().getFirst().rank());
        }
    }

    @Test
    void playerQuitForfeitsImmediatelyAndWinnerCanLeaveFinishedRoom() throws Exception {
        InMemoryUserRepository users = new InMemoryUserRepository();
        ClientSessionState aliceState = new ClientSessionState();
        ClientSessionState bobState = new ClientSessionState();
        try (RunningAuthServer server = new RunningAuthServer(users);
             AuthClientService alice = new AuthClientService(
                     new ClientConfig("127.0.0.1", server.port()),
                     aliceState
             );
             AuthClientService bob = new AuthClientService(
                     new ClientConfig("127.0.0.1", server.port()),
                     bobState
             )) {
            alice.connectAsync().get(3, TimeUnit.SECONDS);
            bob.connectAsync().get(3, TimeUnit.SECONDS);
            alice.register("alice", "secret", "Alice").get(3, TimeUnit.SECONDS);
            bob.register("bob", "secret", "Bob").get(3, TimeUnit.SECONDS);
            alice.login("alice", "secret").get(3, TimeUnit.SECONDS);
            bob.login("bob", "secret").get(3, TimeUnit.SECONDS);

            var room = alice.createRoom().get(3, TimeUnit.SECONDS).room();
            bob.joinRoom(room.roomId()).get(3, TimeUnit.SECONDS);
            alice.setReady(room.roomId(), true).get(3, TimeUnit.SECONDS);
            bob.setReady(room.roomId(), true).get(3, TimeUnit.SECONDS);
            alice.startGame(room.roomId()).get(3, TimeUnit.SECONDS);

            bob.leaveRoom(room.roomId()).get(3, TimeUnit.SECONDS);

            assertTrue(bobState.room().isEmpty());
            assertTrue(bobState.gameState().isEmpty());
            await(() -> aliceState.gameOver().isPresent());
            var finished = aliceState.gameState().orElseThrow();
            assertEquals(RoomState.FINISHED, finished.roomState());
            assertEquals(
                    MatchParticipantStatus.COMPLETED,
                    finished.participants().stream()
                            .filter(participant -> participant.displayName().equals("Alice"))
                            .findFirst()
                            .orElseThrow()
                            .matchStatus()
            );
            var forfeited = finished.participants().stream()
                    .filter(participant -> participant.displayName().equals("Bob"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(MatchParticipantStatus.FORFEITED, forfeited.matchStatus());
            assertEquals(BigDecimal.ZERO, forfeited.scoreEarned());
            assertEquals(
                    vn.ptit.ltm.common.enums.PlayerPresenceState.IDLE,
                    server.sessions().findByUserId(2L).orElseThrow().presenceState()
            );

            alice.leaveRoom(room.roomId()).get(3, TimeUnit.SECONDS);
            assertTrue(aliceState.room().isEmpty());
            assertTrue(aliceState.gameState().isEmpty());
            assertEquals(
                    vn.ptit.ltm.common.enums.PlayerPresenceState.IDLE,
                    server.sessions().findByUserId(1L).orElseThrow().presenceState()
            );
            assertEquals("1", alice.createRoom().get(3, TimeUnit.SECONDS).room().hostPlayerId());
        }
    }

    @Test
    void automaticallyReconnectsDuringTurnAndRestoresFullGameState() throws Exception {
        InMemoryUserRepository users = new InMemoryUserRepository();
        ClientSessionState aliceState = new ClientSessionState();
        ClientSessionState bobState = new ClientSessionState();
        try (RunningAuthServer server = new RunningAuthServer(users);
             AuthClientService alice = new AuthClientService(
                     new ClientConfig("127.0.0.1", server.port()),
                     aliceState
             );
             AuthClientService bob = new AuthClientService(
                     new ClientConfig("127.0.0.1", server.port()),
                     bobState
             )) {
            alice.connectAsync().get(3, TimeUnit.SECONDS);
            bob.connectAsync().get(3, TimeUnit.SECONDS);
            alice.register("alice", "secret", "Alice").get(3, TimeUnit.SECONDS);
            bob.register("bob", "secret", "Bob").get(3, TimeUnit.SECONDS);
            alice.login("alice", "secret").get(3, TimeUnit.SECONDS);
            bob.login("bob", "secret").get(3, TimeUnit.SECONDS);

            var room = alice.createRoom().get(3, TimeUnit.SECONDS).room();
            bob.joinRoom(room.roomId()).get(3, TimeUnit.SECONDS);
            alice.setReady(room.roomId(), true).get(3, TimeUnit.SECONDS);
            bob.setReady(room.roomId(), true).get(3, TimeUnit.SECONDS);
            alice.startGame(room.roomId()).get(3, TimeUnit.SECONDS);
            var dice = alice.rollDice(room.roomId()).get(3, TimeUnit.SECONDS);
            await(() -> aliceState.gameState()
                    .map(game -> game.turnState() == TurnState.WAITING_FOR_MOVE)
                    .orElse(false));
            var beforeDisconnect = aliceState.gameState().orElseThrow();
            assertEquals(TurnState.WAITING_FOR_MOVE, beforeDisconnect.turnState());

            CountDownLatch disconnected = new CountDownLatch(1);
            CountDownLatch reconnected = new CountDownLatch(1);
            AtomicBoolean sawDisconnect = new AtomicBoolean();
            Runnable removeListener = alice.addConnectionStateListener(state -> {
                if (state == ConnectionState.DISCONNECTED) {
                    sawDisconnect.set(true);
                    disconnected.countDown();
                } else if (state == ConnectionState.CONNECTED && sawDisconnect.get()) {
                    reconnected.countDown();
                }
            });
            server.disconnectUser(1L);

            assertTrue(disconnected.await(3, TimeUnit.SECONDS));
            assertTrue(reconnected.await(5, TimeUnit.SECONDS));
            removeListener.run();
            var restored = aliceState.gameState().orElseThrow();
            assertEquals(beforeDisconnect.matchId(), restored.matchId());
            assertEquals(beforeDisconnect.stateVersion(), restored.stateVersion());
            assertEquals(beforeDisconnect.turnState(), restored.turnState());
            assertEquals(
                    beforeDisconnect.serverDeadlineEpochMillis(),
                    restored.serverDeadlineEpochMillis()
            );
            assertEquals(beforeDisconnect.validPieceIds(), restored.validPieceIds());
            assertEquals(
                    vn.ptit.ltm.common.enums.PlayerPresenceState.PLAYING,
                    server.sessions().findByUserId(1L).orElseThrow().presenceState()
            );

            var moved = alice.movePiece(room.roomId(), dice.validPieceIds().getFirst())
                    .get(3, TimeUnit.SECONDS);
            assertEquals(0, moved.piece().stepCount());
        }
    }

    private static final class RunningAuthServer implements AutoCloseable {
        private final ClientSessionState clientSessionState = new ClientSessionState();
        private final SessionManager sessions = new SessionManager(Duration.ofSeconds(2));
        private final HeartbeatManager heartbeat = new HeartbeatManager(Duration.ofSeconds(30), 3);
        private final ConnectionRegistry connections;
        private final RoomService rooms;
        private final TcpServer server;

        private RunningAuthServer(UserRepository users) throws Exception {
            AuthService authService = new AuthService(users, new TestPasswordHasher(), sessions);
            connections = new ConnectionRegistry();
            LobbyService lobby = new LobbyService(sessions, connections);
            rooms = new RoomService(
                    sessions,
                    connections,
                    new SequenceDiceRoller(6, 3)
            );
            InMemoryMatchRepository matches = new InMemoryMatchRepository();
            MatchService matchService = new MatchService(matches, sessions);
            RankingService rankingService = new RankingService(matches);
            sessions.addEventListener(lobby::broadcastOnlinePlayers);
            sessions.addEventListener(rooms::onSessionsChanged);
            AuthMessageHandler handler = new AuthMessageHandler(
                    authService,
                    sessions,
                    session -> new vn.ptit.ltm.common.dto.session.ReconnectResult(
                            true,
                            session.presenceState(),
                            rooms.roomForPlayer(session.user().id()).orElse(null),
                            rooms.gameForPlayer(session.user().id()).orElse(null)
                    ),
                    heartbeat,
                    lobby,
                    rooms,
                    matchService,
                    rankingService
            );
            server = new TcpServer(
                    0,
                    4,
                    handler,
                    new CompositeConnectionListener(
                            connections,
                            heartbeat,
                            new SessionConnectionListener(sessions)
                    )
            );
            server.start();
        }

        private int port() {
            return server.port();
        }

        private ClientSessionState clientSessionState() {
            return clientSessionState;
        }

        private SessionManager sessions() {
            return sessions;
        }

        private void disconnectUser(long userId) {
            var session = sessions.findByUserId(userId).orElseThrow();
            connections.find(session.connectionId()).orElseThrow().close();
        }

        @Override
        public void close() {
            server.close();
            heartbeat.close();
            rooms.close();
            sessions.close();
        }
    }

    private static final class SequenceDiceRoller implements DiceRoller {
        private final ArrayDeque<Integer> values = new ArrayDeque<>();

        private SequenceDiceRoller(int... diceValues) {
            for (int diceValue : diceValues) {
                values.addLast(diceValue);
            }
        }

        @Override
        public synchronized int roll() {
            if (values.isEmpty()) {
                throw new IllegalStateException("No deterministic dice value remains");
            }
            return values.removeFirst();
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "Condition was not met before timeout");
    }

    private static final class TestPasswordHasher implements PasswordHasher {
        @Override
        public String hash(String password) {
            return "hash:" + password;
        }

        @Override
        public boolean matches(String password, String passwordHash) {
            return hash(password).equals(passwordHash);
        }
    }

    private static final class InMemoryUserRepository implements UserRepository {
        private final Map<String, UserAccountRecord> users = new HashMap<>();
        private long nextId = 1;

        @Override
        public synchronized Optional<UserAccountRecord> findByUsername(String username) {
            return Optional.ofNullable(users.get(username.toLowerCase()));
        }

        @Override
        public synchronized long create(String username, String passwordHash, String displayName)
                throws SQLException {
            String key = username.toLowerCase();
            if (users.containsKey(key)) {
                throw new SQLIntegrityConstraintViolationException("duplicate", "23000");
            }
            long id = nextId++;
            Instant now = Instant.now();
            users.put(key, new UserAccountRecord(
                    id,
                    username,
                    passwordHash,
                    displayName,
                    BigDecimal.ZERO,
                    0,
                    now,
                    now
            ));
            return id;
        }
    }

    private static final class InMemoryMatchRepository implements MatchRepository {
        @Override
        public PersistedMatchResult saveCompletedMatch(CompletedMatchRecord match) {
            return new PersistedMatchResult(
                    match.matchId(),
                    match.players().stream()
                            .map(player -> new PersistedPlayerResult(
                                    player.userId(),
                                    player.displayName(),
                                    player.color(),
                                    player.rank(),
                                    player.scoreEarned(),
                                    player.scoreEarned(),
                                    player.rank() == 1 ? 1 : 0,
                                    player.status()
                            ))
                            .toList()
            );
        }

        @Override
        public List<RankingEntryDto> findRanking() {
            return List.of(new RankingEntryDto(
                    1,
                    "1",
                    "Alice",
                    new BigDecimal("3.0"),
                    1
            ));
        }

        @Override
        public List<MatchHistoryEntryDto> findMatchHistory(long userId, int limit) {
            return List.of(new MatchHistoryEntryDto(
                    "stored-match",
                    1_767_225_600_000L,
                    1_767_226_200_000L,
                    2,
                    PieceColor.RED,
                    1,
                    new BigDecimal("3.0"),
                    false
            ));
        }
    }
}
