package vn.ptit.ltm.common.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.error.ErrorPayload;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageEnvelope(
        MessageType type,
        String requestId,
        String sessionId,
        Boolean success,
        JsonNode data,
        ErrorPayload error
) {
    public MessageEnvelope {
        Objects.requireNonNull(type, "type");
    }

    @Override
    public String toString() {
        return "MessageEnvelope[type=" + type
                + ", requestId=" + requestId
                + ", success=" + success
                + ", error=" + error
                + "]";
    }
}
