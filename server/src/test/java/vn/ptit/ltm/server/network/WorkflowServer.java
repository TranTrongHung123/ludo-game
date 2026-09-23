package vn.ptit.ltm.server.network;

import vn.ptit.ltm.common.enums.PieceColor;
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

/** In-memory repositories behind the real TCP server, used only by integration tests. */
final class WorkflowServer implements AutoCloseable {
    private final SessionManager sessions = new SessionManager(Duration.ofSeconds(2));
    private final HeartbeatManager heartbeat = new HeartbeatManager(Duration.ofSeconds(30), 3);
    private final ConnectionRegistry connections;
    private final RoomService rooms;
    private final TcpServer server;

    WorkflowServer(UserRepository users) throws Exception {
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

    int port() {
        return server.port();
    }

    SessionManager sessions() {
        return sessions;
    }


    @Override
    public void close() {
        server.close();
        heartbeat.close();
        rooms.close();
        sessions.close();
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

    static final class InMemoryUserRepository implements UserRepository {
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
