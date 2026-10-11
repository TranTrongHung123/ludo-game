package vn.ptit.ltm.common.dto;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.dto.voice.VoiceRegisterRequest;
import vn.ptit.ltm.common.dto.voice.VoiceRegisteredResult;
import vn.ptit.ltm.common.protocol.JsonMessageCodec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VoiceDtoTest {
    @Test
    void voiceConfigurationRoundTripsThroughJson() throws Exception {
        var mapper = new JsonMessageCodec().objectMapper();
        var expected = new VoiceRegisteredResult(5556, "secure-token", 16000, 1, 320);
        assertEquals(expected, mapper.readValue(mapper.writeValueAsBytes(expected), VoiceRegisteredResult.class));
    }

    @Test
    void rejectsBlankRoomId() {
        assertThrows(IllegalArgumentException.class, () -> new VoiceRegisterRequest("  "));
    }
}
