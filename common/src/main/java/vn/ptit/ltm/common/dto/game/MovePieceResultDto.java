package vn.ptit.ltm.common.dto.game;

import vn.ptit.ltm.common.enums.SpecialCellType;

import java.util.Objects;

public record MovePieceResultDto(
        PieceDto piece,
        String capturedPieceId,
        SpecialCellType triggeredEffect,
        boolean shieldConsumed,
        boolean bonusRoll,
        GameStateDto gameState
) {
    public MovePieceResultDto {
        Objects.requireNonNull(piece, "piece");
        Objects.requireNonNull(gameState, "gameState");
    }
}
