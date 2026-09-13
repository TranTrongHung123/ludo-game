package vn.ptit.ltm.common.dto.game;

import vn.ptit.ltm.common.enums.MatchParticipantStatus;
import vn.ptit.ltm.common.enums.PieceColor;

import java.math.BigDecimal;
import java.util.Objects;

public record MatchStandingDto(
        String playerId,
        String displayName,
        PieceColor color,
        int rank,
        BigDecimal scoreEarned,
        BigDecimal totalScore,
        MatchParticipantStatus matchStatus
) {
    public MatchStandingDto {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(scoreEarned, "scoreEarned");
        Objects.requireNonNull(totalScore, "totalScore");
        Objects.requireNonNull(matchStatus, "matchStatus");
        if (rank < 1 || rank > 4) {
            throw new IllegalArgumentException("rank must be between 1 and 4");
        }
    }
}
