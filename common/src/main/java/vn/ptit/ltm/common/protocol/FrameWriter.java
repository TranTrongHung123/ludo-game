package vn.ptit.ltm.common.protocol;

import vn.ptit.ltm.common.error.ProtocolException;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

public final class FrameWriter {
    public void writeFrame(OutputStream output, byte[] payload) throws IOException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(payload, "payload");
        if (payload.length <= 0 || payload.length > ProtocolConstants.MAX_FRAME_LENGTH) {
            throw new ProtocolException("Invalid frame length: " + payload.length);
        }

        DataOutputStream dataOutput = output instanceof DataOutputStream stream
                ? stream
                : new DataOutputStream(output);
        dataOutput.writeInt(payload.length);
        dataOutput.write(payload);
        dataOutput.flush();
    }
}
