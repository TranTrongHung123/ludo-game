package vn.ptit.ltm.common.dto.voice;

import java.util.Objects;

/** Yêu cầu mở kênh thoại UDP cho trận đấu hiện tại. */
public record VoiceRegisterRequest(String roomId) {
    public VoiceRegisterRequest {
        Objects.requireNonNull(roomId, "roomId");
        if (roomId.isBlank()) {
            throw new IllegalArgumentException("roomId must not be blank");
        }
    }
}
