package vn.ptit.ltm.common.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import vn.ptit.ltm.common.error.ProtocolException;

import java.io.IOException;
import java.util.Objects;

public final class JsonMessageCodec {
    private final ObjectMapper objectMapper;

    public JsonMessageCodec() {
        this(createDefaultObjectMapper());
    }

    public JsonMessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public byte[] encode(MessageEnvelope message) throws ProtocolException {
        Objects.requireNonNull(message, "message");
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(message);
            validateEncodedLength(bytes.length);
            return bytes;
        } catch (JsonProcessingException exception) {
            throw new ProtocolException("Unable to serialize message", exception);
        }
    }

    public MessageEnvelope decode(byte[] payload) throws ProtocolException {
        Objects.requireNonNull(payload, "payload");
        validateEncodedLength(payload.length);
        try {
            return objectMapper.readValue(payload, MessageEnvelope.class);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProtocolException("Malformed JSON message", exception);
        }
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public static ObjectMapper createDefaultObjectMapper() {
        return JsonMapper.builder()
                .defaultPropertyInclusion(JsonInclude.Value.construct(
                        JsonInclude.Include.NON_NULL,
                        JsonInclude.Include.ALWAYS
                ))
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    private static void validateEncodedLength(int length) throws ProtocolException {
        if (length <= 0 || length > ProtocolConstants.MAX_FRAME_LENGTH) {
            throw new ProtocolException("Invalid message length: " + length);
        }
    }
}
