package vn.ptit.ltm.common.dto.auth;

import java.util.Objects;

public record RegisterRequest(String username, String password, String displayName) {
    public RegisterRequest {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(displayName, "displayName");
    }

    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", password=<redacted>, displayName=" + displayName + "]";
    }
}
