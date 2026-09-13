package vn.ptit.ltm.common.dto.game;

import java.util.Objects;

public record RollDiceRequest(String roomId) {
    public RollDiceRequest {
        Objects.requireNonNull(roomId, "roomId");
    }
}
