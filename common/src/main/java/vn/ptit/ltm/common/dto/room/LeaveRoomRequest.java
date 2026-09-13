package vn.ptit.ltm.common.dto.room;

import java.util.Objects;

public record LeaveRoomRequest(String roomId) {
    public LeaveRoomRequest {
        Objects.requireNonNull(roomId, "roomId");
    }
}
