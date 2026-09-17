package vn.ptit.ltm.server.game;

import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.server.service.ServiceException;

public final class GameException extends ServiceException {
    public GameException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
