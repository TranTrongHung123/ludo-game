package vn.ptit.ltm.common.dto.room;

import java.util.Objects;

public record RoomPayload(RoomDto room) {
    public RoomPayload {
        Objects.requireNonNull(room, "room");
    }
}
