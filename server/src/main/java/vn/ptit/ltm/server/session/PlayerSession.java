package vn.ptit.ltm.server.session;

import vn.ptit.ltm.common.enums.PlayerPresenceState;
import vn.ptit.ltm.server.repository.UserAccountRecord;

import java.time.Instant;
import java.util.Objects;

public final class PlayerSession {
    private final String sessionId;
    private final UserAccountRecord user;
    private final Instant createdAt;

    private volatile String connectionId;
    private volatile PlayerPresenceState presenceState;
    private volatile PlayerPresenceState resumePresenceState;
    private volatile Instant disconnectedAt;
    private volatile long connectionGeneration;

    PlayerSession(String sessionId, UserAccountRecord user, String connectionId, Instant now) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.user = Objects.requireNonNull(user, "user");
        this.connectionId = Objects.requireNonNull(connectionId, "connectionId");
        this.createdAt = Objects.requireNonNull(now, "now");
        this.presenceState = PlayerPresenceState.IDLE;
        this.resumePresenceState = PlayerPresenceState.IDLE;
    }

    public String sessionId() {
        return sessionId;
    }

    public UserAccountRecord user() {
        return user;
    }

    public String connectionId() {
        return connectionId;
    }

    public PlayerPresenceState presenceState() {
        return presenceState;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant disconnectedAt() {
        return disconnectedAt;
    }

    public boolean connected() {
        return connectionId != null;
    }

    long markDisconnected(Instant now) {
        if (presenceState != PlayerPresenceState.DISCONNECTED) {
            resumePresenceState = presenceState;
        }
        connectionId = null;
        presenceState = PlayerPresenceState.DISCONNECTED;
        disconnectedAt = now;
        return ++connectionGeneration;
    }

    void reconnect(String newConnectionId) {
        connectionId = Objects.requireNonNull(newConnectionId, "newConnectionId");
        presenceState = resumePresenceState;
        disconnectedAt = null;
        connectionGeneration++;
    }

    void updatePresence(PlayerPresenceState newPresenceState) {
        if (newPresenceState == PlayerPresenceState.OFFLINE
                || newPresenceState == PlayerPresenceState.DISCONNECTED) {
            throw new IllegalArgumentException("Use session lifecycle methods for offline/disconnected states");
        }
        presenceState = Objects.requireNonNull(newPresenceState, "newPresenceState");
        resumePresenceState = newPresenceState;
    }

    void markOffline() {
        connectionId = null;
        presenceState = PlayerPresenceState.OFFLINE;
        disconnectedAt = null;
        connectionGeneration++;
    }

    long connectionGeneration() {
        return connectionGeneration;
    }

    @Override
    public String toString() {
        return "PlayerSession[sessionId=<redacted>, userId=" + user.id()
                + ", presenceState=" + presenceState
                + ", connected=" + connected() + "]";
    }
}
