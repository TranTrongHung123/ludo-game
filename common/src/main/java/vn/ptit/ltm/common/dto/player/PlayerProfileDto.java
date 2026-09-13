package vn.ptit.ltm.common.dto.player;

import java.math.BigDecimal;
import java.util.Objects;

public record PlayerProfileDto(
        String playerId,
        String username,
        String displayName,
        BigDecimal totalScore,
        int firstPlaceCount,
        int totalGames,
        int wins,
        int losses
) {
    public PlayerProfileDto {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(totalScore, "totalScore");
        if (firstPlaceCount < 0 || totalGames < 0 || wins < 0 || losses < 0) {
            throw new IllegalArgumentException("Player statistics must not be negative");
        }
    }
}
