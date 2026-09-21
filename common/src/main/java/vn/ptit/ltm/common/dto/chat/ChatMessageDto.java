package vn.ptit.ltm.common.dto.chat;

import vn.ptit.ltm.common.enums.PieceColor;
import java.util.Objects;

public record ChatMessageDto(
        String messageId,
        String roomId,
        String senderId,
        String senderDisplayName,
        int senderSlotIndex,
        PieceColor senderColor,
        String message,
        long timestampEpochMillis
) {
    public ChatMessageDto {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(senderDisplayName, "senderDisplayName");
        Objects.requireNonNull(senderColor, "senderColor");
        Objects.requireNonNull(message, "message");
    }
}
