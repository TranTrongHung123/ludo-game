package vn.ptit.ltm.common.dto.ranking;

import vn.ptit.ltm.common.enums.PieceColor;
import vn.ptit.ltm.common.model.BoardConstants;

import java.math.BigDecimal;
import java.util.Objects;

public record MatchHistoryEntryDto(
        String matchId,
        long startedAtEpochMillis,
        long endedAtEpochMillis,
        int playerCount,
        PieceColor color,
        int rank,
        BigDecimal scoreEarned,
        boolean forfeited
) {
    public MatchHistoryEntryDto {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(scoreEarned, "scoreEarned");
        if (startedAtEpochMillis <= 0 || endedAtEpochMillis < startedAtEpochMillis) {
            throw new IllegalArgumentException("Invalid match timestamps");
        }
        if (playerCount < BoardConstants.MIN_PLAYERS || playerCount > BoardConstants.MAX_PLAYERS) {
            throw new IllegalArgumentException("playerCount must be between 2 and 4");
        }
        if (rank < 1 || rank > playerCount) {
            throw new IllegalArgumentException("rank must be within playerCount");
        }
        if (forfeited && scoreEarned.signum() != 0) {
            throw new IllegalArgumentException("A forfeited player must earn zero points");
        }
    }
}
