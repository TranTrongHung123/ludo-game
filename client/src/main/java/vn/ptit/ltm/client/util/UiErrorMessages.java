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
