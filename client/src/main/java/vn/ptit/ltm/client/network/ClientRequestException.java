package vn.ptit.ltm.client.network;

import vn.ptit.ltm.common.error.ErrorCode;

import java.util.Objects;

public final class ClientRequestException extends RuntimeException {
    private final ErrorCode errorCode;

    public ClientRequestException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
