package vn.ptit.ltm.common.dto.auth;

import vn.ptit.ltm.common.dto.player.PlayerProfileDto;

import java.util.Objects;

public record RegisterResult(PlayerProfileDto profile) {
    public RegisterResult {
        Objects.requireNonNull(profile, "profile");
    }
}
