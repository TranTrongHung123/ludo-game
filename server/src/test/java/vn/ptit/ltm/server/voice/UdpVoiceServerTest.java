package vn.ptit.ltm.server.voice;

import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UdpVoiceServerTest {
    @Test
    void registersEndpointsAndRelaysAudioOnlyInsideTheSameRoom() throws Exception {
        try (UdpVoiceServer server = new UdpVoiceServer(0, (room, user) -> true);
             DatagramSocket sender = new DatagramSocket();
             DatagramSocket recipient = new DatagramSocket();
             DatagramSocket otherRoom = new DatagramSocket()) {
            server.start();
            var senderConfig = server.register("room-a", 11L, 0);
            var recipientConfig = server.register("room-a", 22L, 1);
            var otherConfig = server.register("room-b", 33L, 0);
            InetSocketAddress target = new InetSocketAddress(InetAddress.getLoopbackAddress(), server.port());

            registerEndpoint(sender, target, senderConfig.token());
            registerEndpoint(recipient, target, recipientConfig.token());
            registerEndpoint(otherRoom, target, otherConfig.token());
            assertRegistrationAck(sender);
            assertRegistrationAck(recipient);
            assertRegistrationAck(otherRoom);

            byte[] pcm = new byte[] {0, 1, 2, 3, 4, 5};
            byte[] audioPacket = clientAudio(senderConfig.token(), 71, pcm);
            sender.send(new DatagramPacket(audioPacket, audioPacket.length, target));

            recipient.setSoTimeout(1_000);
            byte[] received = new byte[128];
            DatagramPacket relayed = new DatagramPacket(received, received.length);
            recipient.receive(relayed);
            ByteBuffer decoded = ByteBuffer.wrap(received, 0, relayed.getLength()).order(ByteOrder.BIG_ENDIAN);
            assertEquals(UdpVoiceServer.MAGIC, decoded.getInt());
            assertEquals(UdpVoiceServer.SERVER_AUDIO, decoded.get());
            assertEquals(11L, decoded.getLong());
            assertEquals(71, decoded.getInt());
            byte[] relayedPcm = new byte[decoded.remaining()];
            decoded.get(relayedPcm);
            assertArrayEquals(pcm, relayedPcm);

            otherRoom.setSoTimeout(150);
            assertThrows(SocketTimeoutException.class, () ->
                    otherRoom.receive(new DatagramPacket(new byte[128], 128))
            );
        }
    }

    @Test
    void rejectsRegistrationWhenPlayerIsNoLongerInMatch() throws Exception {
        try (UdpVoiceServer server = new UdpVoiceServer(0, (room, user) -> false)) {
            assertThrows(IllegalStateException.class, () -> server.register("room-a", 11L, 0));
        }
    }

    private static void registerEndpoint(DatagramSocket socket, InetSocketAddress target, String token)
            throws Exception {
        byte[] decoded = decodeToken(token);
        byte[] packet = ByteBuffer.allocate(UdpVoiceServer.CLIENT_REGISTER_BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(UdpVoiceServer.MAGIC)
                .put(UdpVoiceServer.CLIENT_REGISTER)
                .put(decoded)
                .array();
        socket.send(new DatagramPacket(packet, packet.length, target));
    }

    private static void assertRegistrationAck(DatagramSocket socket) throws Exception {
        socket.setSoTimeout(1_000);
        byte[] bytes = new byte[5];
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
        socket.receive(packet);
        ByteBuffer decoded = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        assertEquals(UdpVoiceServer.MAGIC, decoded.getInt());
        assertEquals(UdpVoiceServer.SERVER_REGISTERED, decoded.get());
    }

    private static byte[] clientAudio(String token, int sequence, byte[] pcm) {
        return ByteBuffer.allocate(UdpVoiceServer.CLIENT_AUDIO_HEADER_BYTES + pcm.length)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(UdpVoiceServer.MAGIC)
                .put(UdpVoiceServer.CLIENT_AUDIO)
                .put(decodeToken(token))
                .putInt(sequence)
                .put(pcm)
                .array();
    }

    private static byte[] decodeToken(String token) {
        return Base64.getUrlDecoder().decode(token);
    }
}
