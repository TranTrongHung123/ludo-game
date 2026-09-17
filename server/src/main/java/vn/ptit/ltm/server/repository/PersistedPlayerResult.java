package vn.ptit.ltm.server.repository;

import vn.ptit.ltm.common.enums.MatchParticipantStatus;
import vn.ptit.ltm.common.enums.PieceColor;

import java.math.BigDecimal;
import java.util.Objects;

public record PersistedPlayerResult(
        long userId,
        String displayName,
        PieceColor color,
        int rank,
        BigDecimal scoreEarned,
        BigDecimal totalScore,
        int firstPlaceCount,
        MatchParticipantStatus status
) {
    public PersistedPlayerResult {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(scoreEarned, "scoreEarned");
        Objects.requireNonNull(totalScore, "totalScore");
        Objects.requireNonNull(status, "status");
        if (userId <= 0 || displayName.isBlank() || rank < 1 || rank > 4
                || scoreEarned.signum() < 0 || totalScore.signum() < 0 || firstPlaceCount < 0) {
            throw new IllegalArgumentException("Persisted player result is invalid");
        }
        if (status == MatchParticipantStatus.ACTIVE) {
            throw new IllegalArgumentException("A persisted result cannot be ACTIVE");
        }
        if (status == MatchParticipantStatus.FORFEITED && scoreEarned.signum() != 0) {
            throw new IllegalArgumentException("A forfeited player must earn zero points");
        }
    }
}
