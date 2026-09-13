package vn.ptit.ltm.common.dto.player;

import vn.ptit.ltm.common.enums.PlayerPresenceState;

import java.math.BigDecimal;
import java.util.Objects;

public record PlayerSummaryDto(
        String playerId,
        String displayName,
        BigDecimal totalScore,
        int firstPlaceCount,
        PlayerPresenceState presenceState
) {
    public PlayerSummaryDto {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(totalScore, "totalScore");
        Objects.requireNonNull(presenceState, "presenceState");
        if (firstPlaceCount < 0) {
            throw new IllegalArgumentException("firstPlaceCount must not be negative");
        }
    }
}
