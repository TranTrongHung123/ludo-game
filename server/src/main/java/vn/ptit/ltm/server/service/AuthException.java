package vn.ptit.ltm.server.service;

import vn.ptit.ltm.common.error.ErrorCode;

public final class AuthException extends ServiceException {

    public AuthException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AuthException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
