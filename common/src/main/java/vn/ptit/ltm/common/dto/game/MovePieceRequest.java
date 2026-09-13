package vn.ptit.ltm.common.dto.game;

import java.util.Objects;

public record MovePieceRequest(String roomId, String pieceId) {
    public MovePieceRequest {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(pieceId, "pieceId");
    }
}
