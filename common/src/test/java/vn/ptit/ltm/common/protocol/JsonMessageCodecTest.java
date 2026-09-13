package vn.ptit.ltm.common.protocol;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.dto.game.MovePieceRequest;
import vn.ptit.ltm.common.dto.auth.LoginRequest;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.common.error.ProtocolException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonMessageCodecTest {
    private final JsonMessageCodec codec = new JsonMessageCodec();
    private final MessageFactory messages = new MessageFactory(codec.objectMapper());
    private final PayloadMapper payloads = new PayloadMapper(codec.objectMapper());

    @Test
    void roundTripsTypedUtf8Request() throws Exception {
        MovePieceRequest request = new MovePieceRequest("phòng-1", "quân-đỏ-1");
        MessageEnvelope message = messages.request(MessageType.MOVE_PIECE, "request-1", "session-1", request);

        MessageEnvelope decoded = codec.decode(codec.encode(message));
        MovePieceRequest decodedPayload = payloads.fromTree(decoded.data(), MovePieceRequest.class);

        assertEquals(message.type(), decoded.type());
        assertEquals("request-1", decoded.requestId());
        assertEquals("session-1", decoded.sessionId());
        assertNull(decoded.success());
        assertEquals(request, decodedPayload);
    }

    @Test
    void createsCanonicalSuccessAndErrorEnvelopes() {
        MessageEnvelope response = messages.response(MessageType.MOVE_PIECE_RESULT, "request-2", null);
        MessageEnvelope error = messages.error("request-3", ErrorCode.NOT_YOUR_TURN, "Chưa đến lượt của bạn");

        assertTrue(response.success());
        assertNull(response.error());
        assertFalse(error.success());
        assertEquals(MessageType.ERROR, error.type());
        assertEquals(ErrorCode.NOT_YOUR_TURN, error.error().code());
    }

    @Test
    void rejectsMalformedOrEmptyJson() {
        assertThrows(ProtocolException.class, () -> codec.decode(new byte[0]));
        assertThrows(ProtocolException.class,
                () -> codec.decode("not-json".getBytes(ProtocolConstants.CHARSET)));
    }

    @Test
    void redactsSecretsFromDiagnosticStrings() {
        LoginRequest login = new LoginRequest("duy", "very-secret-password");
        MessageEnvelope message = messages.request(MessageType.LOGIN, "request-4", "secret-session", login);

        assertFalse(login.toString().contains("very-secret-password"));
        assertFalse(message.toString().contains("secret-session"));
        assertFalse(message.toString().contains("very-secret-password"));
    }
}
