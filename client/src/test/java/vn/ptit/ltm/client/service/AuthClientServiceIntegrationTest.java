package vn.ptit.ltm.client.service;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.client.config.ClientConfig;
import vn.ptit.ltm.client.network.ClientRequestException;
import vn.ptit.ltm.client.state.ClientSessionState;
import vn.ptit.ltm.client.state.ConnectionState;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.server.network.AuthMessageHandler;
import vn.ptit.ltm.server.network.CompositeConnectionListener;
import vn.ptit.ltm.server.network.HeartbeatManager;
import vn.ptit.ltm.server.network.TcpServer;
import vn.ptit.ltm.server.repository.UserAccountRecord;
import vn.ptit.ltm.server.repository.UserRepository;
import vn.ptit.ltm.server.service.AuthService;
import vn.ptit.ltm.server.service.PasswordHasher;
import vn.ptit.ltm.server.session.SessionConnectionListener;
import vn.ptit.ltm.server.session.SessionManager;
import vn.ptit.ltm.server.session.SessionStateProvider;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

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

    private static final class RunningAuthServer implements AutoCloseable {
        private final ClientSessionState clientSessionState = new ClientSessionState();
        private final SessionManager sessions = new SessionManager(Duration.ofSeconds(2));
        private final HeartbeatManager heartbeat = new HeartbeatManager(Duration.ofSeconds(30), 3);
        private final TcpServer server;

        private RunningAuthServer(UserRepository users) throws Exception {
            AuthService authService = new AuthService(users, new TestPasswordHasher(), sessions);
            AuthMessageHandler handler = new AuthMessageHandler(
                    authService,
                    sessions,
                    SessionStateProvider.basic(),
                    heartbeat
            );
            server = new TcpServer(
                    0,
                    4,
                    handler,
                    new CompositeConnectionListener(
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

        @Override
        public void close() {
            server.close();
            heartbeat.close();
            sessions.close();
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
}
