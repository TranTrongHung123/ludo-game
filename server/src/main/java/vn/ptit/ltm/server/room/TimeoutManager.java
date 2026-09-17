package vn.ptit.ltm.server.room;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns one scheduled turn timeout per room. GameRoom still validates the
 * expected state version, phase and player before applying a timeout.
 */
final class TimeoutManager implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimeoutManager.class);

    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final Object lock = new Object();
    private final Map<String, ScheduledEntry> scheduledByRoom = new HashMap<>();
    private final AtomicLong generation = new AtomicLong();

    TimeoutManager(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "turn-timeouts");
            thread.setDaemon(true);
            return thread;
        });
    }

    void schedule(String roomId, long deadlineEpochMillis, Runnable callback) {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(callback, "callback");
        long ticket = generation.incrementAndGet();
        long delayMillis = Math.max(0L, deadlineEpochMillis - clock.millis());
        synchronized (lock) {
            ScheduledEntry previous = scheduledByRoom.remove(roomId);
            if (previous != null) {
                previous.future().cancel(false);
            }
            ScheduledFuture<?> future = scheduler.schedule(
                    () -> runIfCurrent(roomId, ticket, callback),
                    delayMillis,
                    TimeUnit.MILLISECONDS
            );
            scheduledByRoom.put(roomId, new ScheduledEntry(ticket, future));
        }
    }

    void cancel(String roomId) {
        synchronized (lock) {
            ScheduledEntry entry = scheduledByRoom.remove(roomId);
            if (entry != null) {
                entry.future().cancel(false);
            }
        }
    }

    private void runIfCurrent(String roomId, long ticket, Runnable callback) {
        synchronized (lock) {
            ScheduledEntry current = scheduledByRoom.get(roomId);
            if (current == null || current.ticket() != ticket) {
                return;
            }
            scheduledByRoom.remove(roomId);
        }
        try {
            callback.run();
        } catch (RuntimeException exception) {
            LOGGER.error("Turn timeout callback failed for room {}", roomId, exception);
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            scheduledByRoom.values().forEach(entry -> entry.future().cancel(false));
            scheduledByRoom.clear();
        }
        scheduler.shutdownNow();
    }

    private record ScheduledEntry(long ticket, ScheduledFuture<?> future) {
    }
}
