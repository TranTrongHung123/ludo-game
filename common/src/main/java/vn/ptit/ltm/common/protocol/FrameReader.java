package vn.ptit.ltm.common.protocol;

import vn.ptit.ltm.common.error.ProtocolException;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class FrameReader {
    public byte[] readFrame(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        DataInputStream dataInput = input instanceof DataInputStream stream
                ? stream
                : new DataInputStream(input);

        int length = dataInput.readInt();
        if (length <= 0 || length > ProtocolConstants.MAX_FRAME_LENGTH) {
            throw new ProtocolException("Invalid frame length: " + length);
        }

        byte[] payload = new byte[length];
        dataInput.readFully(payload);
        return payload;
    }
}
