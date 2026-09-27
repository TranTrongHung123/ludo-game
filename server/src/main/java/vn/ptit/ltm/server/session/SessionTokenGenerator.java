package vn.ptit.ltm.server.session;

import java.security.SecureRandom;
import java.util.Base64;

final class SessionTokenGenerator {
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    // Sinh mã phiên bằng nguồn ngẫu nhiên an toàn để khó đoán token của người khác.
    String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
