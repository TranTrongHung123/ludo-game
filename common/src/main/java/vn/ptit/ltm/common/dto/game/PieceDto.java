package vn.ptit.ltm.common.dto.game;

import vn.ptit.ltm.common.enums.PieceColor;
import vn.ptit.ltm.common.enums.PieceState;
import vn.ptit.ltm.common.model.BoardConstants;

import java.util.Objects;

public record PieceDto(
        String pieceId,
        String ownerPlayerId,
        PieceColor color,
        PieceState state,
        int stepCount,
        boolean slowed,
        boolean shielded
) {
    public PieceDto {
        Objects.requireNonNull(pieceId, "pieceId");
        Objects.requireNonNull(ownerPlayerId, "ownerPlayerId");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(state, "state");
        boolean valid = switch (state) {
            case IN_YARD -> stepCount == BoardConstants.YARD_STEP;
            case ON_TRACK -> stepCount >= BoardConstants.FIRST_TRACK_STEP
                    && stepCount <= BoardConstants.LAST_RING_STEP;
            case IN_FINISH_TRACK -> stepCount >= BoardConstants.FIRST_FINISH_STEP
                    && stepCount < BoardConstants.LAST_FINISH_STEP;
            case FINISHED -> stepCount == BoardConstants.LAST_FINISH_STEP;
        };
        if (!valid) {
            throw new IllegalArgumentException("stepCount does not match piece state: " + state);
        }
    }
}
