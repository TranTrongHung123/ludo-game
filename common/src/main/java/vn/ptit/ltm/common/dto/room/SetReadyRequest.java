package vn.ptit.ltm.common.dto.room;

import java.util.Objects;

public record SetReadyRequest(String roomId, boolean ready) {
    public SetReadyRequest {
        Objects.requireNonNull(roomId, "roomId");
    }
}
