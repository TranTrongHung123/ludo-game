package vn.ptit.ltm.server.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.ptit.ltm.common.dto.session.HeartbeatPayload;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.protocol.JsonMessageCodec;
import vn.ptit.ltm.common.protocol.MessageFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class HeartbeatManager implements ConnectionListener, AutoCloseable {
    public static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(10);
    public static final int DEFAULT_MAX_MISSED_HEARTBEATS = 3;

    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatManager.class);

    private final Duration interval;
    private final int maxMissedHeartbeats;
    private final ScheduledExecutorService scheduler;
    private final MessageFactory messageFactory;
    private final Map<String, HeartbeatState> states = new ConcurrentHashMap<>();

    public HeartbeatManager() {
        this(DEFAULT_INTERVAL, DEFAULT_MAX_MISSED_HEARTBEATS);
    }

    public HeartbeatManager(Duration interval, int maxMissedHeartbeats) {
        this.interval = Objects.requireNonNull(interval, "interval");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        if (maxMissedHeartbeats <= 0) {
            throw new IllegalArgumentException("maxMissedHeartbeats must be positive");
        }
        this.maxMissedHeartbeats = maxMissedHeartbeats;
        this.messageFactory = new MessageFactory(new JsonMessageCodec().objectMapper());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(
                this::tickSafely,
                interval.toMillis(),
                interval.toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public void onConnected(ClientConnection connection) {
        states.put(connection.id(), new HeartbeatState(connection));
    }

    @Override
    public void onDisconnected(ClientConnection connection) {
        states.remove(connection.id());
    }

    public void recordPong(ClientConnection connection) {
        HeartbeatState state = states.get(connection.id());
        if (state != null) {
            state.acknowledge();
        }
    }

    int trackedConnectionCount() {
        return states.size();
    }

    private void tickSafely() {
        try {
            tick();
        } catch (RuntimeException exception) {
            LOGGER.error("Heartbeat task failed", exception);
        }
    }

    private void tick() {
        for (HeartbeatState state : states.values()) {
            ClientConnection connection = state.connection();
            if (!connection.isOpen()) {
                states.remove(connection.id(), state);
                continue;
            }
            if (state.recordMissIfAwaiting() >= maxMissedHeartbeats) {
                LOGGER.info("Closing client {} after {} missed heartbeats", connection.id(), maxMissedHeartbeats);
                states.remove(connection.id(), state);
                connection.close();
                continue;
            }
            try {
                connection.send(messageFactory.event(
                        MessageType.PING,
                        new HeartbeatPayload(System.currentTimeMillis())
                ));
                state.markPingSent();
            } catch (IOException exception) {
                LOGGER.debug("Heartbeat send failed for client {}: {}", connection.id(), exception.getMessage());
                states.remove(connection.id(), state);
                connection.close();
            }
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        states.clear();
    }

    private static final class HeartbeatState {
        private final ClientConnection connection;
        private boolean awaitingPong;
        private int missedHeartbeats;

        private HeartbeatState(ClientConnection connection) {
            this.connection = connection;
        }

        private ClientConnection connection() {
            return connection;
        }

        private synchronized int recordMissIfAwaiting() {
            if (awaitingPong) {
                missedHeartbeats++;
            }
            return missedHeartbeats;
        }

        private synchronized void markPingSent() {
            awaitingPong = true;
        }

        private synchronized void acknowledge() {
            awaitingPong = false;
            missedHeartbeats = 0;
        }
    }
}
