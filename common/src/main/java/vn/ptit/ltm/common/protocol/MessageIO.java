package vn.ptit.ltm.common.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

public final class MessageIO {
    private final FrameReader frameReader;
    private final FrameWriter frameWriter;
    private final JsonMessageCodec messageCodec;

    public MessageIO() {
        this(new FrameReader(), new FrameWriter(), new JsonMessageCodec());
    }

    public MessageIO(FrameReader frameReader, FrameWriter frameWriter, JsonMessageCodec messageCodec) {
        this.frameReader = Objects.requireNonNull(frameReader, "frameReader");
        this.frameWriter = Objects.requireNonNull(frameWriter, "frameWriter");
        this.messageCodec = Objects.requireNonNull(messageCodec, "messageCodec");
    }

    public MessageEnvelope read(InputStream input) throws IOException {
        return messageCodec.decode(frameReader.readFrame(input));
    }

    public void write(OutputStream output, MessageEnvelope message) throws IOException {
        frameWriter.writeFrame(output, messageCodec.encode(message));
    }
}
