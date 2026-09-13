package vn.ptit.ltm.common.dto.session;

import java.util.Objects;

public record ReconnectRequest(String sessionId) {
    public ReconnectRequest {
        Objects.requireNonNull(sessionId, "sessionId");
    }

    @Override
    public String toString() {
        return "ReconnectRequest[sessionId=<redacted>]";
    }
}
