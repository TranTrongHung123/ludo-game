package vn.ptit.ltm.client.state;

import vn.ptit.ltm.common.dto.auth.LoginResult;
import vn.ptit.ltm.common.dto.player.PlayerProfileDto;

import java.util.Objects;
import java.util.Optional;

public final class ClientSessionState {
    private String sessionId;
    private PlayerProfileDto profile;

    public synchronized void authenticate(LoginResult result) {
        Objects.requireNonNull(result, "result");
        sessionId = result.sessionId();
        profile = result.profile();
    }

    public synchronized Optional<PlayerProfileDto> profile() {
        return Optional.ofNullable(profile);
    }

    public synchronized boolean isAuthenticated() {
        return sessionId != null && profile != null;
    }

    public synchronized String requireSessionId() {
        if (sessionId == null) {
            throw new IllegalStateException("Client is not authenticated");
        }
        return sessionId;
    }

    public synchronized void clear() {
        sessionId = null;
        profile = null;
    }
}
