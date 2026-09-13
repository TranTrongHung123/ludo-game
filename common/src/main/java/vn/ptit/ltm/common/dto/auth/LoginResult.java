package vn.ptit.ltm.common.dto.auth;

import vn.ptit.ltm.common.dto.player.PlayerProfileDto;

import java.util.Objects;

public record LoginResult(String sessionId, PlayerProfileDto profile) {
    public LoginResult {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(profile, "profile");
    }

    @Override
    public String toString() {
        return "LoginResult[sessionId=<redacted>, profile=" + profile + "]";
    }
}
