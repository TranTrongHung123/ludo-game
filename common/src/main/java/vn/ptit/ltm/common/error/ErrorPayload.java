package vn.ptit.ltm.common.error;

import java.util.Objects;

public record ErrorPayload(ErrorCode code, String message) {
    public ErrorPayload {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}
