package vn.ptit.ltm.common.dto.ranking;

import java.math.BigDecimal;
import java.util.Objects;

public record RankingEntryDto(
        int rank,
        String playerId,
        String displayName,
        BigDecimal totalScore,
        int firstPlaceCount
) {
    public RankingEntryDto {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(totalScore, "totalScore");
        if (rank < 1 || firstPlaceCount < 0) {
            throw new IllegalArgumentException("Invalid ranking values");
        }
    }
}
