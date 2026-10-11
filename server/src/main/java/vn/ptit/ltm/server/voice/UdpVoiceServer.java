package vn.ptit.ltm.server.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.ptit.ltm.common.dto.voice.VoiceRegisteredResult;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;

/** UDP relay xác thực bằng token TCP và cô lập âm thanh theo từng phòng. */
public final class UdpVoiceServer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(UdpVoiceServer.class);

    static final int MAGIC = 0x4C564331; // "LVC1"
    static final byte CLIENT_REGISTER = 1;
    static final byte CLIENT_AUDIO = 2;
    static final byte SERVER_AUDIO = 3;
    static final byte SERVER_REGISTERED = 4;
    static final int TOKEN_BYTES = 32;
    static final int CLIENT_REGISTER_BYTES = Integer.BYTES + 1 + TOKEN_BYTES;
    static final int CLIENT_AUDIO_HEADER_BYTES = CLIENT_REGISTER_BYTES + Integer.BYTES;
    static final int SERVER_AUDIO_HEADER_BYTES = Integer.BYTES + 1 + Long.BYTES + Integer.BYTES;
    static final int MAX_AUDIO_BYTES = 960;
    private static final int MAX_PACKET_BYTES = CLIENT_AUDIO_HEADER_BYTES + MAX_AUDIO_BYTES;
    private static final long ENDPOINT_TTL_MILLIS = 15_000;

    public static final int SAMPLE_RATE = 16_000;
    public static final int CHANNELS = 1;
    public static final int FRAME_SAMPLES = 320;

    private final DatagramSocket socket;
    private final BiPredicate<String, Long> participantValidator;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Registration> registrationsByToken = new ConcurrentHashMap<>();
    private final Map<Long, String> tokenByUserId = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private Thread receiverThread;

    public UdpVoiceServer(int port, BiPredicate<String, Long> participantValidator) throws SocketException {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("UDP port must be between 0 and 65535");
        }
        this.participantValidator = Objects.requireNonNull(participantValidator, "participantValidator");
        this.socket = new DatagramSocket(port);
        this.socket.setSoTimeout(1_000);
    }

    public int port() {
        return socket.getLocalPort();
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        receiverThread = new Thread(this::receiveLoop, "udp-voice-relay");
        receiverThread.setDaemon(true);
        receiverThread.start();
        LOGGER.info("UDP voice relay started on port {}", port());
    }

    /** Cấp token mới; token cũ của cùng người chơi hết hiệu lực ngay. */
    public VoiceRegisteredResult register(String roomId, long userId, int slotIndex) {
        Objects.requireNonNull(roomId, "roomId");
        if (!participantValidator.test(roomId, userId)) {
            throw new IllegalStateException("Player is not an active voice participant");
        }

        byte[] token = new byte[TOKEN_BYTES];
        random.nextBytes(token);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        Registration registration = new Registration(roomId, userId, slotIndex);
        String previous = tokenByUserId.put(userId, encoded);
        if (previous != null) {
            registrationsByToken.remove(previous);
        }
        registrationsByToken.put(encoded, registration);
        return new VoiceRegisteredResult(port(), encoded, SAMPLE_RATE, CHANNELS, FRAME_SAMPLES);
    }

    public void unregister(long userId) {
        String token = tokenByUserId.remove(userId);
        if (token != null) {
            registrationsByToken.remove(token);
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[MAX_PACKET_BYTES];
        while (running.get()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                handlePacket(packet);
            } catch (SocketTimeoutException ignored) {
                removeExpiredEndpoints(System.currentTimeMillis());
            } catch (SocketException exception) {
                if (running.get()) {
                    LOGGER.warn("UDP voice socket stopped unexpectedly", exception);
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.debug("Dropped invalid UDP voice packet: {}", exception.getMessage());
            }
        }
    }

    private void handlePacket(DatagramPacket packet) throws IOException {
        if (packet.getLength() < CLIENT_REGISTER_BYTES) {
            return;
        }
        ByteBuffer input = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength())
                .order(ByteOrder.BIG_ENDIAN);
        if (input.getInt() != MAGIC) {
            return;
        }
        byte type = input.get();
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        input.get(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Registration registration = registrationsByToken.get(token);
        if (registration == null || !participantValidator.test(registration.roomId, registration.userId)) {
            if (registration != null) {
                unregister(registration.userId);
            }
            return;
        }

        InetSocketAddress source = new InetSocketAddress(packet.getAddress(), packet.getPort());
        long now = System.currentTimeMillis();
        if (type == CLIENT_REGISTER && packet.getLength() == CLIENT_REGISTER_BYTES) {
            registration.endpoint = source;
            registration.lastSeenMillis = now;
            sendRegistrationAck(source);
            return;
        }
        if (type != CLIENT_AUDIO || packet.getLength() < CLIENT_AUDIO_HEADER_BYTES + 2) {
            return;
        }
        if (!source.equals(registration.endpoint)) {
            return;
        }
        int audioBytes = packet.getLength() - CLIENT_AUDIO_HEADER_BYTES;
        if (audioBytes > MAX_AUDIO_BYTES || (audioBytes & 1) != 0) {
            return;
        }
        int sequence = input.getInt();
        registration.lastSeenMillis = now;
        relay(registration, sequence, packet.getData(), packet.getOffset() + CLIENT_AUDIO_HEADER_BYTES, audioBytes, now);
    }

    private void relay(
            Registration sender,
            int sequence,
            byte[] audio,
            int audioOffset,
            int audioLength,
            long now
    ) throws IOException {
        byte[] outbound = new byte[SERVER_AUDIO_HEADER_BYTES + audioLength];
        ByteBuffer output = ByteBuffer.wrap(outbound).order(ByteOrder.BIG_ENDIAN);
        output.putInt(MAGIC);
        output.put(SERVER_AUDIO);
        output.putLong(sender.userId);
        output.putInt(sequence);
        output.put(audio, audioOffset, audioLength);

        for (Registration recipient : registrationsByToken.values()) {
            SocketAddress endpoint = recipient.endpoint;
            if (recipient.userId == sender.userId
                    || !recipient.roomId.equals(sender.roomId)
                    || endpoint == null
                    || now - recipient.lastSeenMillis > ENDPOINT_TTL_MILLIS) {
                continue;
            }
            if (!participantValidator.test(recipient.roomId, recipient.userId)) {
                unregister(recipient.userId);
                continue;
            }
            socket.send(new DatagramPacket(outbound, outbound.length, endpoint));
        }
    }

    private void sendRegistrationAck(SocketAddress target) throws IOException {
        byte[] bytes = ByteBuffer.allocate(Integer.BYTES + 1)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(MAGIC)
                .put(SERVER_REGISTERED)
                .array();
        socket.send(new DatagramPacket(bytes, bytes.length, target));
    }

    private void removeExpiredEndpoints(long now) {
        for (Registration registration : registrationsByToken.values()) {
            if (!participantValidator.test(registration.roomId, registration.userId)) {
                unregister(registration.userId);
                continue;
            }
            if (registration.endpoint != null && now - registration.lastSeenMillis > ENDPOINT_TTL_MILLIS) {
                registration.endpoint = null;
            }
        }
    }

    @Override
    public void close() {
        boolean wasRunning = running.getAndSet(false);
        socket.close();
        if (wasRunning && receiverThread != null && receiverThread != Thread.currentThread()) {
            try {
                receiverThread.join(2_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        registrationsByToken.clear();
        tokenByUserId.clear();
        LOGGER.info("UDP voice relay stopped");
    }

    private static final class Registration {
        private final String roomId;
        private final long userId;
        @SuppressWarnings("unused")
        private final int slotIndex;
        private volatile InetSocketAddress endpoint;
        private volatile long lastSeenMillis;

        private Registration(String roomId, long userId, int slotIndex) {
            this.roomId = roomId;
            this.userId = userId;
            this.slotIndex = slotIndex;
        }
    }
}
