package vn.ptit.ltm.common.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

public final class PayloadMapper {
    private final ObjectMapper objectMapper;

    public PayloadMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode toTree(Object payload) {
        return payload == null ? null : objectMapper.valueToTree(payload);
    }

    public <T> T fromTree(JsonNode data, Class<T> payloadType) throws JsonProcessingException {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(payloadType, "payloadType");
        return objectMapper.treeToValue(data, payloadType);
    }
}
