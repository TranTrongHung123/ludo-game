package vn.ptit.ltm.common.dto.chat;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.Objects;

public record SendChatMessageRequest(
        String roomId,
        @JsonAlias({"content", "text"})
        String message
) {
    public static final int MAX_MESSAGE_LENGTH = 200;

    public SendChatMessageRequest {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(message, "message");
        if (roomId.isBlank()) {
            throw new IllegalArgumentException("roomId must not be blank");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message must not exceed " + MAX_MESSAGE_LENGTH + " characters");
        }
    }
}
