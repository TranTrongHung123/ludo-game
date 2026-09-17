package vn.ptit.ltm.server.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.ptit.ltm.common.enums.PlayerPresenceState;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.server.repository.UserAccountRecord;
import vn.ptit.ltm.server.service.AuthException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SessionManager implements AutoCloseable {
    public static final Duration DEFAULT_RECONNECT_GRACE_PERIOD = Duration.ofSeconds(60);
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionManager.class);

    private final Duration reconnectGracePeriod;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService eventExecutor;
    private final SessionTokenGenerator tokenGenerator = new SessionTokenGenerator();
    private final Map<String, PlayerSession> sessionsById = new HashMap<>();
    private final Map<Long, String> sessionIdByUserId = new HashMap<>();
    private final Map<String, String> sessionIdByConnectionId = new HashMap<>();
    private final CopyOnWriteArrayList<SessionEventListener> eventListeners = new CopyOnWriteArrayList<>();

    public SessionManager() {
        this(DEFAULT_RECONNECT_GRACE_PERIOD);
    }

    public SessionManager(Duration reconnectGracePeriod) {
        this(
                reconnectGracePeriod,
                Clock.systemUTC(),
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "session-expiration");
                    thread.setDaemon(true);
                    return thread;
                })
        );
    }

    SessionManager(Duration reconnectGracePeriod, Clock clock, ScheduledExecutorService scheduler) {
        this.reconnectGracePeriod = Objects.requireNonNull(reconnectGracePeriod, "reconnectGracePeriod");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.eventExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "session-events");
            thread.setDaemon(true);
            return thread;
        });
        if (reconnectGracePeriod.isZero() || reconnectGracePeriod.isNegative()) {
            throw new IllegalArgumentException("reconnectGracePeriod must be positive");
        }
    }

    public synchronized PlayerSession createSession(UserAccountRecord user, String connectionId) {
        Objects.requireNonNull(user, "user");
        requireConnectionId(connectionId);
        if (sessionIdByUserId.containsKey(user.id())) {
            throw new AuthException(
                    ErrorCode.ACCOUNT_ALREADY_LOGGED_IN,
                    "Account already has an active session"
            );
        }
        ensureConnectionIsUnbound(connectionId);

        String sessionId;
        do {
            sessionId = tokenGenerator.generate();
        } while (sessionsById.containsKey(sessionId));

        PlayerSession session = new PlayerSession(sessionId, user, connectionId, clock.instant());
        sessionsById.put(sessionId, session);
        sessionIdByUserId.put(user.id(), sessionId);
        sessionIdByConnectionId.put(connectionId, sessionId);
        publishSessionsChanged();
        return session;
    }

    public synchronized PlayerSession requireAuthenticated(String sessionId, String connectionId) {
        PlayerSession session = requireSession(sessionId);
        if (!session.connected() || !Objects.equals(session.connectionId(), connectionId)) {
            throw new AuthException(ErrorCode.UNAUTHORIZED, "Session is not bound to this connection");
        }
        return session;
    }

    public synchronized PlayerSession reconnect(String sessionId, String newConnectionId) {
        requireConnectionId(newConnectionId);
        PlayerSession session = requireSession(sessionId);
        if (session.connected()) {
            throw new AuthException(
                    ErrorCode.ACCOUNT_ALREADY_LOGGED_IN,
                    "Session is still connected"
            );
        }
        if (isGracePeriodExpired(session)) {
            removeSession(session);
            throw new AuthException(ErrorCode.SESSION_EXPIRED, "Reconnect grace period has expired");
        }
        ensureConnectionIsUnbound(newConnectionId);
        session.reconnect(newConnectionId);
        sessionIdByConnectionId.put(newConnectionId, session.sessionId());
        publishSessionsChanged();
        return session;
    }

    public synchronized void logout(String sessionId, String connectionId) {
        PlayerSession session = requireAuthenticated(sessionId, connectionId);
        removeSession(session);
        publishSessionsChanged();
    }

    public synchronized void disconnect(String connectionId) {
        if (connectionId == null) {
            return;
        }
        String sessionId = sessionIdByConnectionId.remove(connectionId);
        if (sessionId == null) {
            return;
        }
        PlayerSession session = sessionsById.get(sessionId);
        if (session == null || !Objects.equals(connectionId, session.connectionId())) {
            return;
        }
        long generation = session.markDisconnected(clock.instant());
        scheduler.schedule(
                () -> expireDisconnectedSession(sessionId, generation),
                reconnectGracePeriod.toMillis(),
                TimeUnit.MILLISECONDS
        );
        publishSessionsChanged();
    }

    public synchronized void updatePresence(String sessionId, PlayerPresenceState presenceState) {
        PlayerSession session = requireSession(sessionId);
        if (!session.connected()) {
            throw new AuthException(ErrorCode.UNAUTHORIZED, "Disconnected session cannot change presence");
        }
        session.updatePresence(presenceState);
        publishSessionsChanged();
    }

    public synchronized void updatePresenceForUser(long userId, PlayerPresenceState presenceState) {
        String sessionId = sessionIdByUserId.get(userId);
        if (sessionId == null) {
            return;
        }
        PlayerSession session = sessionsById.get(sessionId);
        if (session == null) {
            return;
        }
        session.updatePresenceForSystem(presenceState);
        publishSessionsChanged();
    }

    public synchronized Optional<PlayerSession> findBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionsById.get(sessionId));
    }

    public synchronized Optional<PlayerSession> findByConnectionId(String connectionId) {
        String sessionId = sessionIdByConnectionId.get(connectionId);
        return sessionId == null ? Optional.empty() : Optional.ofNullable(sessionsById.get(sessionId));
    }

    public synchronized Optional<PlayerSession> findByUserId(long userId) {
        String sessionId = sessionIdByUserId.get(userId);
        return sessionId == null ? Optional.empty() : Optional.ofNullable(sessionsById.get(sessionId));
    }

    public synchronized int activeSessionCount() {
        return sessionsById.size();
    }

    public synchronized List<SessionSnapshot> snapshots() {
        return sessionsById.values().stream()
                .map(session -> new SessionSnapshot(
                        session.user().id(),
                        session.user().displayName(),
                        session.user().score(),
                        session.user().firstPlaceCount(),
                        session.presenceState()
                ))
                .toList();
    }

    public void addEventListener(SessionEventListener listener) {
        eventListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeEventListener(SessionEventListener listener) {
        eventListeners.remove(listener);
    }

    public Duration reconnectGracePeriod() {
        return reconnectGracePeriod;
    }

    private synchronized void expireDisconnectedSession(String sessionId, long expectedGeneration) {
        PlayerSession session = sessionsById.get(sessionId);
        if (session == null
                || session.connected()
                || session.connectionGeneration() != expectedGeneration
                || !isGracePeriodExpired(session)) {
            return;
        }
        removeSession(session);
        publishSessionsChanged();
    }

    private boolean isGracePeriodExpired(PlayerSession session) {
        Instant disconnectedAt = session.disconnectedAt();
        return disconnectedAt == null
                || !clock.instant().isBefore(disconnectedAt.plus(reconnectGracePeriod));
    }

    private PlayerSession requireSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new AuthException(ErrorCode.UNAUTHORIZED, "Session ID is required");
        }
        PlayerSession session = sessionsById.get(sessionId);
        if (session == null) {
            throw new AuthException(ErrorCode.SESSION_EXPIRED, "Session does not exist or has expired");
        }
        return session;
    }

    private void ensureConnectionIsUnbound(String connectionId) {
        if (sessionIdByConnectionId.containsKey(connectionId)) {
            throw new AuthException(ErrorCode.INVALID_REQUEST, "Connection is already authenticated");
        }
    }

    private void removeSession(PlayerSession session) {
        sessionsById.remove(session.sessionId());
        sessionIdByUserId.remove(session.user().id(), session.sessionId());
        if (session.connectionId() != null) {
            sessionIdByConnectionId.remove(session.connectionId(), session.sessionId());
        }
        session.markOffline();
    }

    private static void requireConnectionId(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            throw new IllegalArgumentException("connectionId must not be blank");
        }
    }

    private void publishSessionsChanged() {
        if (eventListeners.isEmpty()) {
            return;
        }
        try {
            eventExecutor.execute(() -> {
                for (SessionEventListener listener : eventListeners) {
                    try {
                        listener.onSessionsChanged();
                    } catch (RuntimeException exception) {
                        LOGGER.error("Session event listener failed", exception);
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            LOGGER.debug("Ignoring session event after shutdown");
        }
    }

    @Override
    public synchronized void close() {
        sessionsById.values().forEach(PlayerSession::markOffline);
        sessionsById.clear();
        sessionIdByUserId.clear();
        sessionIdByConnectionId.clear();
        eventListeners.clear();
        scheduler.shutdownNow();
        eventExecutor.shutdownNow();
    }
}
