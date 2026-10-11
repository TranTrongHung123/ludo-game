package vn.ptit.ltm.common.dto.voice;

import java.util.Objects;

/** Cấu hình và token để client đăng ký endpoint UDP. */
public record VoiceRegisteredResult(
        int udpPort,
        String token,
        int sampleRate,
        int channels,
        int frameSamples
) {
    public VoiceRegisteredResult {
        if (udpPort < 1 || udpPort > 65_535) {
            throw new IllegalArgumentException("udpPort must be valid");
        }
        Objects.requireNonNull(token, "token");
        if (token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        if (sampleRate <= 0 || channels != 1 || frameSamples <= 0) {
            throw new IllegalArgumentException("invalid voice audio format");
        }
    }
}
