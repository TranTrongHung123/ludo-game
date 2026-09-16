package vn.ptit.ltm.server.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.ptit.ltm.common.dto.EmptyPayload;
import vn.ptit.ltm.common.dto.auth.LoginRequest;
import vn.ptit.ltm.common.dto.auth.LoginResult;
import vn.ptit.ltm.common.dto.auth.RegisterRequest;
import vn.ptit.ltm.common.dto.session.ReconnectRequest;
import vn.ptit.ltm.common.dto.session.ReconnectResult;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.enums.PlayerPresenceState;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.common.protocol.JsonMessageCodec;
import vn.ptit.ltm.common.protocol.MessageEnvelope;
import vn.ptit.ltm.common.protocol.MessageFactory;
import vn.ptit.ltm.common.protocol.MessageIO;
import vn.ptit.ltm.common.protocol.PayloadMapper;
import vn.ptit.ltm.server.network.AuthMessageHandler;
import vn.ptit.ltm.server.network.CompositeConnectionListener;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TESTS", matches = "true")
class AuthSessionIntegrationTest {
    @Test
    void registerLoginDisconnectReconnectAndLogoutOverTcp() throws Exception {
        DatabaseConfig databaseConfig = DatabaseConfig.fromEnvironment();
        String username = "auth_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        JsonMessageCodec codec = new JsonMessageCodec();
        MessageFactory messages = new MessageFactory(codec.objectMapper());
        PayloadMapper payloads = new PayloadMapper(codec.objectMapper());

        try (DatabaseManager database = DatabaseManager.initialize(databaseConfig)) {
            JdbcUserRepository users = new JdbcUserRepository(database.dataSource());
            try (SessionManager sessions = new SessionManager(Duration.ofSeconds(2));
                 HeartbeatManager heartbeat = new HeartbeatManager(Duration.ofHours(1), 3)) {
                AuthService auth = new AuthService(users, new BCryptPasswordHasher(), sessions);
                AuthMessageHandler handler = new AuthMessageHandler(
                        auth,
                        sessions,
                        SessionStateProvider.basic(),
                        heartbeat
                );
                try (TcpServer server = new TcpServer(
                        0,
                        4,
                        handler,
                        new CompositeConnectionListener(
                                heartbeat,
                                new SessionConnectionListener(sessions)
                        )
                )) {
                    server.start();
                    runFlow(server.port(), username, sessions, users, messages, payloads);
                }
            } finally {
                deleteTestUser(database, username);
            }
        }
    }

    private static void runFlow(
            int port,
            String username,
            SessionManager sessions,
            JdbcUserRepository users,
            MessageFactory messages,
            PayloadMapper payloads
    ) throws Exception {
        MessageIO firstIo = new MessageIO();
        Socket firstConnection = new Socket("127.0.0.1", port);
        try {
            MessageEnvelope register = exchange(
                    firstConnection,
                    firstIo,
                    messages.request(
                            MessageType.REGISTER,
                            "register-1",
                            null,
                            new RegisterRequest(username, "integration-password", "Integration User")
                    )
            );
            assertEquals(Boolean.TRUE, register.success());
            assertEquals(MessageType.REGISTER, register.type());
            assertTrue(users.findByUsername(username).orElseThrow().passwordHash().startsWith("$2"));

            MessageEnvelope duplicate = exchange(
                    firstConnection,
                    firstIo,
                    messages.request(
                            MessageType.REGISTER,
                            "register-duplicate",
                            null,
                            new RegisterRequest(username, "integration-password", "Integration User")
                    )
            );
            assertEquals(MessageType.ERROR, duplicate.type());
            assertEquals(ErrorCode.USERNAME_ALREADY_EXISTS, duplicate.error().code());

            MessageEnvelope loginEnvelope = exchange(
                    firstConnection,
                    firstIo,
                    messages.request(
                            MessageType.LOGIN,
                            "login-1",
                            null,
                            new LoginRequest(username, "integration-password")
                    )
            );
            assertEquals(Boolean.TRUE, loginEnvelope.success());
            LoginResult login = payloads.fromTree(loginEnvelope.data(), LoginResult.class);
            assertNotNull(login.sessionId());
            assertFalse(login.sessionId().isBlank());

            try (Socket secondConnection = new Socket("127.0.0.1", port)) {
                MessageIO secondIo = new MessageIO();
                MessageEnvelope secondLogin = exchange(
                        secondConnection,
                        secondIo,
                        messages.request(
                                MessageType.LOGIN,
                                "login-2",
                                null,
                                new LoginRequest(username, "integration-password")
                        )
                );
                assertEquals(MessageType.ERROR, secondLogin.type());
                assertEquals(ErrorCode.ACCOUNT_ALREADY_LOGGED_IN, secondLogin.error().code());

                firstConnection.close();
                assertTrue(waitUntil(
                        () -> sessions.findBySessionId(login.sessionId())
                                .map(session -> session.presenceState() == PlayerPresenceState.DISCONNECTED)
                                .orElse(false),
                        Duration.ofSeconds(2)
                ));

                MessageEnvelope reconnectEnvelope = exchange(
                        secondConnection,
                        secondIo,
                        messages.request(
                                MessageType.RECONNECT,
                                "reconnect-1",
                                null,
                                new ReconnectRequest(login.sessionId())
                        )
                );
                assertEquals(MessageType.RECONNECT_RESULT, reconnectEnvelope.type());
                ReconnectResult reconnect = payloads.fromTree(
                        reconnectEnvelope.data(),
                        ReconnectResult.class
                );
                assertTrue(reconnect.restored());
                assertEquals(PlayerPresenceState.IDLE, reconnect.presenceState());

                MessageEnvelope logout = exchange(
                        secondConnection,
                        secondIo,
                        messages.request(
                                MessageType.LOGOUT,
                                "logout-1",
                                login.sessionId(),
                                EmptyPayload.INSTANCE
                        )
                );
                assertEquals(MessageType.LOGOUT, logout.type());
                assertEquals(Boolean.TRUE, logout.success());
                assertEquals(0, sessions.activeSessionCount());
            }
        } finally {
            firstConnection.close();
        }
    }

    private static MessageEnvelope exchange(
            Socket socket,
            MessageIO messageIO,
            MessageEnvelope request
    ) throws Exception {
        messageIO.write(socket.getOutputStream(), request);
        return messageIO.read(socket.getInputStream());
    }

    private static void deleteTestUser(DatabaseManager database, String username) throws Exception {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM users WHERE username = ?"
             )) {
            statement.setString(1, username);
            statement.executeUpdate();
        }
    }

    private static boolean waitUntil(java.util.function.BooleanSupplier condition, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }
}
