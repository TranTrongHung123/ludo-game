package vn.ptit.ltm.common.protocol;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.error.ProtocolException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrameCodecTest {
    private final FrameReader reader = new FrameReader();
    private final FrameWriter writer = new FrameWriter();

    @Test
    void readsConsecutiveUtf8Frames() throws Exception {
        byte[] first = "Xin chào".getBytes(ProtocolConstants.CHARSET);
        byte[] second = "Cờ Cá Ngựa".getBytes(ProtocolConstants.CHARSET);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writer.writeFrame(output, first);
        writer.writeFrame(output, second);

        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        assertArrayEquals(first, reader.readFrame(input));
        assertArrayEquals(second, reader.readFrame(input));
        assertEquals(0, input.available());
    }

    @Test
    void acceptsFrameAtExactMaximumSize() throws Exception {
        byte[] payload = new byte[ProtocolConstants.MAX_FRAME_LENGTH];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.writeFrame(output, payload);
        assertArrayEquals(payload, reader.readFrame(new ByteArrayInputStream(output.toByteArray())));
    }

    @Test
    void rejectsInvalidLengthsBeforeReadingPayload() throws Exception {
        assertThrows(ProtocolException.class, () -> reader.readFrame(streamWithLength(0)));
        assertThrows(ProtocolException.class, () -> reader.readFrame(streamWithLength(-1)));
        assertThrows(ProtocolException.class,
                () -> reader.readFrame(streamWithLength(ProtocolConstants.MAX_FRAME_LENGTH + 1)));
        assertThrows(ProtocolException.class,
                () -> writer.writeFrame(new ByteArrayOutputStream(), new byte[0]));
    }

    @Test
    void reportsDisconnectInMiddleOfFrame() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream dataOutput = new DataOutputStream(output);
        dataOutput.writeInt(10);
        dataOutput.write(new byte[3]);
        assertThrows(EOFException.class,
                () -> reader.readFrame(new ByteArrayInputStream(output.toByteArray())));
    }

    private static ByteArrayInputStream streamWithLength(int length) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new DataOutputStream(output).writeInt(length);
        return new ByteArrayInputStream(output.toByteArray());
    }
}
