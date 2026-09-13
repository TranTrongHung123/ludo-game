package vn.ptit.ltm.common.protocol;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class ProtocolConstants {
    public static final int MAX_FRAME_LENGTH = 64 * 1024;
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    private ProtocolConstants() {
    }
}
