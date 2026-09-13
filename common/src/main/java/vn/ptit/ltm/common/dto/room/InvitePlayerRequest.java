package vn.ptit.ltm.common.dto.room;

import java.util.Objects;

public record InvitePlayerRequest(String roomId, String playerId) {
    public InvitePlayerRequest {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(playerId, "playerId");
    }
}
