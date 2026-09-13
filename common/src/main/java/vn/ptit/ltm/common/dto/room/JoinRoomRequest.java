package vn.ptit.ltm.common.dto.room;

import java.util.Objects;

public record JoinRoomRequest(String roomId) {
    public JoinRoomRequest {
        Objects.requireNonNull(roomId, "roomId");
    }
}
