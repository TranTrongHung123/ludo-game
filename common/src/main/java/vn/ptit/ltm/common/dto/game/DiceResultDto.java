package vn.ptit.ltm.common.dto.game;

import vn.ptit.ltm.common.model.GameConstants;

import java.util.List;
import java.util.Objects;

public record DiceResultDto(
        String roomId,
        String playerId,
        int diceValue,
        List<String> validPieceIds
) {
    public DiceResultDto {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(playerId, "playerId");
        validPieceIds = validPieceIds == null ? List.of() : List.copyOf(validPieceIds);
        if (diceValue < GameConstants.DICE_MIN || diceValue > GameConstants.DICE_MAX) {
            throw new IllegalArgumentException("diceValue must be between 1 and 6");
        }
    }

    public boolean hasValidMoves() {
        return !validPieceIds.isEmpty();
    }
}
