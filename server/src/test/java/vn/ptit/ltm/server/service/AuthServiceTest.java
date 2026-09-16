package vn.ptit.ltm.server.service;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.dto.auth.LoginRequest;
import vn.ptit.ltm.common.dto.auth.LoginResult;
import vn.ptit.ltm.common.dto.auth.RegisterRequest;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.server.repository.UserAccountRecord;
import vn.ptit.ltm.server.repository.UserRepository;
import vn.ptit.ltm.server.session.SessionManager;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {
    @Test
    void registersAndHashesPasswordWithoutReturningIt() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(1))) {
            AuthService auth = new AuthService(users, new TestPasswordHasher(), sessions);

            var result = auth.register(new RegisterRequest("alice", "secret", "Alice"));

            UserAccountRecord stored = users.findByUsername("alice").orElseThrow();
            assertEquals("hash:secret", stored.passwordHash());
            assertEquals("alice", result.profile().username());
            assertTrue(result.toString().contains("alice"));
            assertTrue(!result.toString().contains("secret"));
        }
    }

    @Test
    void logsInAndRejectsASecondSessionForTheSameAccount() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        users.add(user(1, "alice", "hash:secret"));
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(1))) {
            AuthService auth = new AuthService(users, new TestPasswordHasher(), sessions);

            LoginResult login = auth.login(new LoginRequest("alice", "secret"), "connection-1");
            assertTrue(login.sessionId().length() >= 40);

            AuthException exception = assertThrows(
                    AuthException.class,
                    () -> auth.login(new LoginRequest("alice", "secret"), "connection-2")
            );
            assertEquals(ErrorCode.ACCOUNT_ALREADY_LOGGED_IN, exception.errorCode());
        }
    }

    @Test
    void returnsSameErrorForUnknownUserAndWrongPassword() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        users.add(user(1, "alice", "hash:secret"));
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(1))) {
            AuthService auth = new AuthService(users, new TestPasswordHasher(), sessions);

            AuthException unknown = assertThrows(
                    AuthException.class,
                    () -> auth.login(new LoginRequest("unknown", "wrong"), "connection-1")
            );
            AuthException wrong = assertThrows(
                    AuthException.class,
                    () -> auth.login(new LoginRequest("alice", "wrong"), "connection-2")
            );
            assertEquals(ErrorCode.INVALID_CREDENTIALS, unknown.errorCode());
            assertEquals(unknown.errorCode(), wrong.errorCode());
            assertEquals(unknown.getMessage(), wrong.getMessage());
        }
    }

    @Test
    void validatesBcryptByteLimit() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(1))) {
            AuthService auth = new AuthService(users, new TestPasswordHasher(), sessions);
            String oversizedUtf8Password = "ắ".repeat(25);

            AuthException exception = assertThrows(
                    AuthException.class,
                    () -> auth.register(new RegisterRequest("alice", oversizedUtf8Password, "Alice"))
            );
            assertEquals(ErrorCode.INVALID_REQUEST, exception.errorCode());
        }
    }

    private static UserAccountRecord user(long id, String username, String passwordHash) {
        Instant now = Instant.now();
        return new UserAccountRecord(
                id,
                username,
                passwordHash,
                "Alice",
                BigDecimal.ZERO,
                0,
                now,
                now
        );
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
        public Optional<UserAccountRecord> findByUsername(String username) {
            return Optional.ofNullable(users.get(username.toLowerCase()));
        }

        @Override
        public long create(String username, String passwordHash, String displayName) throws SQLException {
            String key = username.toLowerCase();
            if (users.containsKey(key)) {
                throw new java.sql.SQLIntegrityConstraintViolationException("duplicate", "23000");
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

        private void add(UserAccountRecord user) {
            users.put(user.username().toLowerCase(), user);
            nextId = Math.max(nextId, user.id() + 1);
        }
    }
}
