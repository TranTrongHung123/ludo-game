package vn.ptit.ltm.server.network;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestIdRegistryTest {
    @Test
    void rejectsReplayOnSameConnectionButAllowsIndependentRequests() {
        RequestIdRegistry registry = new RequestIdRegistry();

        assertTrue(registry.register("connection-a", "request-1"));
        assertFalse(registry.register("connection-a", "request-1"));
        assertTrue(registry.register("connection-a", "request-2"));
        assertTrue(registry.register("connection-b", "request-1"));
    }

    @Test
    void retainsOnlyBoundedRecentIdsAndExpiresTheRetryWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RequestIdRegistry registry = new RequestIdRegistry(2, Duration.ofSeconds(10), clock);

        assertTrue(registry.register("connection", "request-1"));
        assertTrue(registry.register("connection", "request-2"));
        assertTrue(registry.register("connection", "request-3"));
        assertTrue(registry.register("connection", "request-1"), "oldest bounded entry was evicted");
        assertFalse(registry.register("connection", "request-3"));

        clock.advance(Duration.ofSeconds(10));
        assertTrue(registry.register("connection", "request-3"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
