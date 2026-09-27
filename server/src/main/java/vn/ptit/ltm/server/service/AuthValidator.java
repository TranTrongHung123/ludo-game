package vn.ptit.ltm.server.service;

import vn.ptit.ltm.common.error.ErrorCode;

import java.nio.charset.StandardCharsets;

final class AuthValidator {
    private static final int MAX_USERNAME_LENGTH = 50;
    private static final int MAX_DISPLAY_NAME_LENGTH = 100;
    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

    private AuthValidator() {
    }

    // Áp dụng kiểm tra username, mật khẩu và tên hiển thị trước khi lưu.
    static void validateRegistration(String username, String password, String displayName) {
        validateUsername(username);
        validatePassword(password);
        validateDisplayName(displayName);
    }

    // Kiểm tra dữ liệu đăng nhập trước khi truy vấn tài khoản.
    static void validateLogin(String username, String password) {
        validateUsername(username);
        validatePassword(password);
    }

    // Giới hạn độ dài và từ chối khoảng trắng ở hai đầu tên đăng nhập.
    private static void validateUsername(String username) {
        requireText(username, "Username");
        if (username.length() > MAX_USERNAME_LENGTH) {
            throw invalid("Username must not exceed " + MAX_USERNAME_LENGTH + " characters");
        }
        if (!username.equals(username.trim())) {
            throw invalid("Username must not start or end with whitespace");
        }
    }

    // Kiểm tra độ dài mật khẩu theo byte UTF-8 phù hợp với BCrypt.
    private static void validatePassword(String password) {
        requireText(password, "Password");
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            throw invalid("Password must not exceed 72 UTF-8 bytes");
        }
    }

    // Chặn tên hiển thị rỗng, quá dài hoặc có khoảng trắng ở hai đầu.
    private static void validateDisplayName(String displayName) {
        requireText(displayName, "Display name");
        if (displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw invalid("Display name must not exceed " + MAX_DISPLAY_NAME_LENGTH + " characters");
        }
        if (!displayName.equals(displayName.trim())) {
            throw invalid("Display name must not start or end with whitespace");
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw invalid(label + " must not be blank");
        }
    }

    private static AuthException invalid(String message) {
        return new AuthException(ErrorCode.INVALID_REQUEST, message);
    }
}
