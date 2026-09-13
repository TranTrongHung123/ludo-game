package vn.ptit.ltm.common.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.common.error.ErrorPayload;

import java.util.Objects;

public final class MessageFactory {
    private final PayloadMapper payloadMapper;

    public MessageFactory(ObjectMapper objectMapper) {
        this.payloadMapper = new PayloadMapper(objectMapper);
    }

    public MessageEnvelope request(
            MessageType type,
            String requestId,
            String sessionId,
            Object payload
    ) {
        requireRequestId(requestId);
        return new MessageEnvelope(type, requestId, sessionId, null, payloadMapper.toTree(payload), null);
    }

    public MessageEnvelope response(MessageType type, String requestId, Object payload) {
        requireRequestId(requestId);
        return new MessageEnvelope(type, requestId, null, true, payloadMapper.toTree(payload), null);
    }

    public MessageEnvelope event(MessageType type, Object payload) {
        return new MessageEnvelope(type, null, null, null, payloadMapper.toTree(payload), null);
    }

    public MessageEnvelope error(String requestId, ErrorCode code, String message) {
        return new MessageEnvelope(
                MessageType.ERROR,
                requestId,
                null,
                false,
                null,
                new ErrorPayload(code, message)
        );
    }

    private static void requireRequestId(String requestId) {
        Objects.requireNonNull(requestId, "requestId");
        if (requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
    }
}
