package vn.ptit.ltm.server.session;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.enums.PlayerPresenceState;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.server.repository.UserAccountRecord;
import vn.ptit.ltm.server.service.AuthException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionManagerTest {
    @Test
    void allowsOnlyOneActiveSessionPerAccount() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(1))) {
            PlayerSession first = sessions.createSession(user(1), "connection-1");

            AuthException exception = assertThrows(
                    AuthException.class,
                    () -> sessions.createSession(user(1), "connection-2")
            );
            assertEquals(ErrorCode.ACCOUNT_ALREADY_LOGGED_IN, exception.errorCode());
            assertEquals(first, sessions.requireAuthenticated(first.sessionId(), "connection-1"));
        }
    }

    @Test
    void reconnectsWithinGracePeriodAndRestoresPresence() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(1))) {
            PlayerSession session = sessions.createSession(user(1), "connection-1");
            sessions.updatePresence(session.sessionId(), PlayerPresenceState.PLAYING);

            sessions.disconnect("connection-1");
            assertFalse(session.connected());
            assertEquals(PlayerPresenceState.DISCONNECTED, session.presenceState());

            PlayerSession restored = sessions.reconnect(session.sessionId(), "connection-2");
            assertEquals(session.sessionId(), restored.sessionId());
            assertEquals(PlayerPresenceState.PLAYING, restored.presenceState());
            assertTrue(restored.connected());
            assertEquals("connection-2", restored.connectionId());
        }
    }

    @Test
    void expiresDisconnectedSessionAfterGracePeriod() throws Exception {
        try (SessionManager sessions = new SessionManager(Duration.ofMillis(60))) {
            PlayerSession expired = sessions.createSession(user(1), "connection-1");
            sessions.disconnect("connection-1");

            assertTrue(waitUntil(() -> sessions.activeSessionCount() == 0, Duration.ofSeconds(2)));
            AuthException exception = assertThrows(
                    AuthException.class,
                    () -> sessions.reconnect(expired.sessionId(), "connection-2")
            );
            assertEquals(ErrorCode.SESSION_EXPIRED, exception.errorCode());

            PlayerSession replacement = sessions.createSession(user(1), "connection-2");
            assertNotEquals(expired.sessionId(), replacement.sessionId());
        }
    }

    @Test
    void logoutImmediatelyCleansUpSession() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(1))) {
            PlayerSession session = sessions.createSession(user(1), "connection-1");

            sessions.logout(session.sessionId(), "connection-1");

            assertEquals(0, sessions.activeSessionCount());
            assertEquals(PlayerPresenceState.OFFLINE, session.presenceState());
            assertTrue(sessions.findByConnectionId("connection-1").isEmpty());
        }
    }

    private static UserAccountRecord user(long id) {
        Instant now = Instant.now();
        return new UserAccountRecord(
                id,
                "user" + id,
                "hash",
                "User " + id,
                BigDecimal.ZERO,
                0,
                now,
                now
        );
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
