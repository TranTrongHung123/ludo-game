package vn.ptit.ltm.server.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.ptit.ltm.common.dto.auth.LoginRequest;
import vn.ptit.ltm.common.dto.auth.LoginResult;
import vn.ptit.ltm.common.dto.auth.RegisterRequest;
import vn.ptit.ltm.common.dto.lobby.OnlinePlayersPayload;
import vn.ptit.ltm.common.dto.player.PlayerSummaryDto;
import vn.ptit.ltm.common.dto.EmptyPayload;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.enums.PlayerPresenceState;
import vn.ptit.ltm.common.protocol.JsonMessageCodec;
import vn.ptit.ltm.common.protocol.MessageEnvelope;
import vn.ptit.ltm.common.protocol.MessageFactory;
import vn.ptit.ltm.common.protocol.MessageIO;
import vn.ptit.ltm.common.protocol.PayloadMapper;
import vn.ptit.ltm.server.lobby.LobbyService;
import vn.ptit.ltm.server.network.AuthMessageHandler;
import vn.ptit.ltm.server.network.CompositeConnectionListener;
import vn.ptit.ltm.server.network.ConnectionRegistry;
import vn.ptit.ltm.server.network.HeartbeatManager;
import vn.ptit.ltm.server.network.TcpServer;
import vn.ptit.ltm.server.repository.JdbcUserRepository;
import vn.ptit.ltm.server.service.AuthService;
import vn.ptit.ltm.server.service.BCryptPasswordHasher;
import vn.ptit.ltm.server.session.SessionConnectionListener;
import vn.ptit.ltm.server.session.SessionManager;
import vn.ptit.ltm.server.session.SessionStateProvider;

import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TESTS", matches = "true")
class LobbyIntegrationTest {
    @Test
    void listsAndBroadcastsAuthoritativePlayerPresence() throws Exception {
        DatabaseConfig databaseConfig = DatabaseConfig.fromEnvironment();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String firstUsername = "lobby_a_" + suffix;
        String secondUsername = "lobby_b_" + suffix;
        JsonMessageCodec codec = new JsonMessageCodec();
        MessageFactory messages = new MessageFactory(codec.objectMapper());
        PayloadMapper payloads = new PayloadMapper(codec.objectMapper());

        try (DatabaseManager database = DatabaseManager.initialize(databaseConfig)) {
            try (SessionManager sessions = new SessionManager(Duration.ofSeconds(5));
                 HeartbeatManager heartbeat = new HeartbeatManager(Duration.ofHours(1), 3)) {
                ConnectionRegistry connections = new ConnectionRegistry();
                LobbyService lobby = new LobbyService(sessions, connections);
                sessions.addEventListener(lobby::broadcastOnlinePlayers);
                AuthMessageHandler handler = new AuthMessageHandler(
                        new AuthService(
                                new JdbcUserRepository(database.dataSource()),
                                new BCryptPasswordHasher(),
                                sessions
                        ),
                        sessions,
                        SessionStateProvider.basic(),
                        heartbeat,
                        lobby
                );
                try (TcpServer server = new TcpServer(
                        0,
                        4,
                        handler,
                        new CompositeConnectionListener(
                                connections,
                                heartbeat,
                                new SessionConnectionListener(sessions)
                        )
                )) {
                    server.start();
                    runFlow(
                            server.port(),
                            firstUsername,
                            secondUsername,
                            sessions,
                            messages,
                            payloads
                    );
                }
            } finally {
                deleteTestUsers(database, firstUsername, secondUsername);
            }
        }
    }

    private static void runFlow(
            int port,
            String firstUsername,
            String secondUsername,
            SessionManager sessions,
            MessageFactory messages,
            PayloadMapper payloads
    ) throws Exception {
        try (TestPeer first = new TestPeer(port); TestPeer second = new TestPeer(port)) {
            register(first, firstUsername, "Lobby Alpha", messages);
            register(second, secondUsername, "Lobby Beta", messages);

            LoginResult firstLogin = login(first, firstUsername, "login-a", messages, payloads);
            LoginResult secondLogin = login(second, secondUsername, "login-b", messages, payloads);
            String secondPlayerId = secondLogin.profile().playerId();

            OnlinePlayersPayload twoIdlePlayers = first.awaitLobby(
                    payloads,
                    payload -> payload.players().size() == 2
                            && presence(payload, secondPlayerId) == PlayerPresenceState.IDLE
            );
            assertEquals(2, twoIdlePlayers.players().size());

            MessageEnvelope onlineResponse = first.exchange(
                    messages.request(
                            MessageType.GET_ONLINE_PLAYERS,
                            "online-list",
                            firstLogin.sessionId(),
                            EmptyPayload.INSTANCE
                    )
            );
            assertEquals(MessageType.ONLINE_PLAYERS_UPDATED, onlineResponse.type());
            assertEquals(Boolean.TRUE, onlineResponse.success());
            OnlinePlayersPayload requested = payloads.fromTree(
                    onlineResponse.data(),
                    OnlinePlayersPayload.class
            );
            assertEquals(2, requested.players().size());
            assertTrue(requested.players().stream().allMatch(player -> player.totalScore().signum() == 0));
            assertTrue(requested.players().stream().allMatch(player -> player.firstPlaceCount() == 0));

            sessions.updatePresence(secondLogin.sessionId(), PlayerPresenceState.IN_ROOM);
            first.awaitLobby(
                    payloads,
                    payload -> presence(payload, secondPlayerId) == PlayerPresenceState.IN_ROOM
            );

            sessions.updatePresence(secondLogin.sessionId(), PlayerPresenceState.PLAYING);
            first.awaitLobby(
                    payloads,
                    payload -> presence(payload, secondPlayerId) == PlayerPresenceState.PLAYING
            );

            second.close();
            first.awaitLobby(
                    payloads,
                    payload -> presence(payload, secondPlayerId) == PlayerPresenceState.DISCONNECTED
            );
        }
    }

    private static void register(TestPeer peer, String username, String displayName, MessageFactory messages)
            throws Exception {
        MessageEnvelope response = peer.exchange(messages.request(
                MessageType.REGISTER,
                "register-" + username,
                null,
                new RegisterRequest(username, "integration-password", displayName)
        ));
        assertEquals(Boolean.TRUE, response.success());
    }

    private static LoginResult login(
            TestPeer peer,
            String username,
            String requestId,
            MessageFactory messages,
            PayloadMapper payloads
    ) throws Exception {
        MessageEnvelope response = peer.exchange(messages.request(
                MessageType.LOGIN,
                requestId,
                null,
                new LoginRequest(username, "integration-password")
        ));
        assertEquals(Boolean.TRUE, response.success());
        return payloads.fromTree(response.data(), LoginResult.class);
    }

    private static PlayerPresenceState presence(OnlinePlayersPayload payload, String playerId) {
        return payload.players().stream()
                .filter(player -> player.playerId().equals(playerId))
                .map(PlayerSummaryDto::presenceState)
                .findFirst()
                .orElse(null);
    }

    private static void deleteTestUsers(DatabaseManager database, String... usernames) throws Exception {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM users WHERE username = ?"
             )) {
            for (String username : usernames) {
                statement.setString(1, username);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static final class TestPeer implements AutoCloseable {
        private final Socket socket;
        private final MessageIO messageIO = new MessageIO();
        private final List<MessageEnvelope> pendingEvents = new ArrayList<>();

        private TestPeer(int port) throws Exception {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(3_000);
        }

        private MessageEnvelope exchange(MessageEnvelope request) throws Exception {
            messageIO.write(socket.getOutputStream(), request);
            while (true) {
                MessageEnvelope message = messageIO.read(socket.getInputStream());
                if (request.requestId().equals(message.requestId())) {
                    return message;
                }
                pendingEvents.add(message);
            }
        }

        private OnlinePlayersPayload awaitLobby(
                PayloadMapper payloads,
                Predicate<OnlinePlayersPayload> predicate
        ) throws Exception {
            for (int index = 0; index < pendingEvents.size(); index++) {
                MessageEnvelope event = pendingEvents.get(index);
                if (event.type() == MessageType.ONLINE_PLAYERS_UPDATED) {
                    OnlinePlayersPayload payload = payloads.fromTree(
                            event.data(),
                            OnlinePlayersPayload.class
                    );
                    if (predicate.test(payload)) {
                        pendingEvents.remove(index);
                        return payload;
                    }
                }
            }
            while (true) {
                MessageEnvelope event = messageIO.read(socket.getInputStream());
                if (event.type() != MessageType.ONLINE_PLAYERS_UPDATED) {
                    pendingEvents.add(event);
                    continue;
                }
                OnlinePlayersPayload payload = payloads.fromTree(
                        event.data(),
                        OnlinePlayersPayload.class
                );
                if (predicate.test(payload)) {
                    return payload;
                }
                pendingEvents.add(event);
            }
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }
}
