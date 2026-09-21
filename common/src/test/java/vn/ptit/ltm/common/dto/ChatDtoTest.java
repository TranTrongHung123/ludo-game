package vn.ptit.ltm.common.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.dto.chat.ChatMessageDto;
import vn.ptit.ltm.common.dto.chat.SendChatMessageRequest;
import vn.ptit.ltm.common.enums.PieceColor;

import static org.junit.jupiter.api.Assertions.*;

class ChatDtoTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sendChatMessageRequestValidation() {
        assertDoesNotThrow(() -> new SendChatMessageRequest("room-1", "Hello"));

        assertThrows(NullPointerException.class, () -> new SendChatMessageRequest(null, "Hello"));
        assertThrows(NullPointerException.class, () -> new SendChatMessageRequest("room-1", null));
        assertThrows(IllegalArgumentException.class, () -> new SendChatMessageRequest("", "Hello"));
        assertThrows(IllegalArgumentException.class, () -> new SendChatMessageRequest("   ", "Hello"));
        assertThrows(IllegalArgumentException.class, () -> new SendChatMessageRequest("room-1", ""));
        assertThrows(IllegalArgumentException.class, () -> new SendChatMessageRequest("room-1", "   "));
        assertThrows(IllegalArgumentException.class, () -> new SendChatMessageRequest("room-1", "a".repeat(201)));
    }

    @Test
    void deserializesWithContentAlias() throws Exception {
        String json = "{\"roomId\":\"room-1\",\"content\":\"Test message\"}";
        SendChatMessageRequest request = mapper.readValue(json, SendChatMessageRequest.class);
        assertEquals("room-1", request.roomId());
        assertEquals("Test message", request.message());
    }

    @Test
    void chatMessageDtoSerializationRoundTrip() throws Exception {
        ChatMessageDto dto = new ChatMessageDto(
                "msg-1",
                "room-1",
                "player-1",
                "Player One",
                0,
                PieceColor.RED,
                "Hello all!",
                123456789L
        );
        String json = mapper.writeValueAsString(dto);
        ChatMessageDto deserialized = mapper.readValue(json, ChatMessageDto.class);
        assertEquals(dto, deserialized);
    }
}
