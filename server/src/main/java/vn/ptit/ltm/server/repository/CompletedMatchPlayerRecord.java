package vn.ptit.ltm.server.repository;

import vn.ptit.ltm.common.enums.MatchParticipantStatus;
import vn.ptit.ltm.common.enums.PieceColor;

import java.math.BigDecimal;
import java.util.Objects;

public record CompletedMatchPlayerRecord(
        long userId,
        String displayName,
        PieceColor color,
        int rank,
        BigDecimal scoreEarned,
        MatchParticipantStatus status
) {
    public CompletedMatchPlayerRecord {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(scoreEarned, "scoreEarned");
        Objects.requireNonNull(status, "status");
        if (userId <= 0 || displayName.isBlank()) {
            throw new IllegalArgumentException("Completed-match player identity is invalid");
        }
        if (rank < 1 || rank > 4 || scoreEarned.signum() < 0) {
            throw new IllegalArgumentException("Completed-match rank or score is invalid");
        }
        if (status == MatchParticipantStatus.ACTIVE) {
            throw new IllegalArgumentException("A completed match cannot contain ACTIVE players");
        }
        if (status == MatchParticipantStatus.FORFEITED && scoreEarned.signum() != 0) {
            throw new IllegalArgumentException("A forfeited player must earn zero points");
        }
    }
}
