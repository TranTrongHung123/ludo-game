package vn.ptit.ltm.common.dto.room;

import java.util.Objects;

public record StartGameRequest(String roomId) {
    public StartGameRequest {
        Objects.requireNonNull(roomId, "roomId");
    }
}
