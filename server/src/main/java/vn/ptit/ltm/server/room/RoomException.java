package vn.ptit.ltm.server.room;

import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.server.service.ServiceException;

public final class RoomException extends ServiceException {
    public RoomException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
