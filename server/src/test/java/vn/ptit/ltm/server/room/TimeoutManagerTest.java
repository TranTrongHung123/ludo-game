package vn.ptit.ltm.server.room;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeoutManagerTest {
    @Test
    void replacingRoomDeadlineCancelsTheStaleCallback() throws InterruptedException {
        AtomicInteger staleRuns = new AtomicInteger();
        AtomicInteger currentRuns = new AtomicInteger();
        CountDownLatch currentFired = new CountDownLatch(1);

        try (TimeoutManager manager = new TimeoutManager(Clock.systemUTC())) {
            manager.schedule(
                    "room-1",
                    System.currentTimeMillis() + 250L,
                    staleRuns::incrementAndGet
            );
            manager.schedule(
                    "room-1",
                    System.currentTimeMillis() + 20L,
                    () -> {
                        currentRuns.incrementAndGet();
                        currentFired.countDown();
                    }
            );

            assertTrue(currentFired.await(1, TimeUnit.SECONDS));
            Thread.sleep(300L);
            assertEquals(0, staleRuns.get());
            assertEquals(1, currentRuns.get());
        }
    }
}
