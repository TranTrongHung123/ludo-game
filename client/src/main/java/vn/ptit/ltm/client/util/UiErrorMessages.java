package vn.ptit.ltm.client.util;

import vn.ptit.ltm.client.network.ClientRequestException;

import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public final class UiErrorMessages {
    private UiErrorMessages() {
    }

    public static String from(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof ClientRequestException requestException) {
            return switch (requestException.errorCode()) {
                case USERNAME_ALREADY_EXISTS -> "Tên đăng nhập đã được sử dụng.";
                case INVALID_CREDENTIALS -> "Tên đăng nhập hoặc mật khẩu không đúng.";
                case ACCOUNT_ALREADY_LOGGED_IN -> "Tài khoản này đang đăng nhập ở nơi khác.";
                case INVALID_REQUEST -> "Thông tin gửi lên chưa hợp lệ.";
                case SESSION_EXPIRED, UNAUTHORIZED -> "Phiên đăng nhập không còn hợp lệ.";
                case ROOM_NOT_FOUND -> "Không tìm thấy phòng.";
                case ROOM_FULL -> "Phòng đã đủ 4 người.";
                case ALREADY_IN_ROOM -> "Bạn đã ở trong một phòng.";
                case NOT_IN_ROOM -> "Bạn không còn ở trong phòng này.";
                case PLAYER_NOT_IDLE -> "Bạn phải ở trạng thái rảnh để vào phòng.";
                case GAME_ALREADY_STARTED -> "Trận đấu trong phòng đã bắt đầu.";
                case NOT_ROOM_HOST -> "Chỉ chủ phòng được thực hiện thao tác này.";
                case INVITATION_NOT_FOUND -> "Lời mời không còn tồn tại.";
                case INVITATION_EXPIRED -> "Lời mời đã hết hạn.";
                case NOT_ENOUGH_PLAYERS -> "Cần ít nhất 2 người để bắt đầu.";
                case PLAYER_NOT_READY -> "Tất cả người chơi phải kết nối và sẵn sàng.";
                case GAME_NOT_STARTED -> "Trận đấu chưa bắt đầu hoặc đã kết thúc.";
                case NOT_YOUR_TURN -> "Chưa đến lượt của bạn.";
                case ROLL_NOT_ALLOWED -> "Hiện tại không thể đổ xúc xắc.";
                case MOVE_NOT_ALLOWED -> "Hiện tại không thể di chuyển quân.";
                case INVALID_MOVE -> "Quân này không có nước đi hợp lệ.";
                case NO_VALID_MOVE -> "Không có quân nào có thể di chuyển.";
                case PIECE_NOT_FOUND -> "Không tìm thấy quân cờ.";
                case PIECE_ALREADY_FINISHED -> "Quân này đã về đích.";
                case TURN_TIMEOUT -> "Lượt chơi đã hết thời gian.";
                default -> "Server không thể xử lý yêu cầu lúc này.";
            };
        }
        if (cause instanceof TimeoutException) {
            return "Server phản hồi quá lâu. Vui lòng thử lại.";
        }
        if (cause instanceof IOException) {
            return "Không thể kết nối tới Game Server.";
        }
        return "Đã xảy ra lỗi. Vui lòng thử lại.";
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
