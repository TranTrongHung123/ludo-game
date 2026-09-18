package vn.ptit.ltm.server.network;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.dto.session.HeartbeatPayload;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.protocol.JsonMessageCodec;
import vn.ptit.ltm.common.protocol.MessageEnvelope;
import vn.ptit.ltm.common.protocol.MessageFactory;
import vn.ptit.ltm.common.protocol.MessageIO;
import vn.ptit.ltm.common.protocol.ProtocolConstants;

import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TcpServerTest {
    @Test
    void handlesPingPongOverFramedJson() throws Exception {
        CountDownLatch pongReceived = new CountDownLatch(1);
        MessageFactory messageFactory = new MessageFactory(new JsonMessageCodec().objectMapper());
        ConnectionListener listener = new ConnectionListener() {
            @Override
            public void onConnected(ClientConnection connection) {
                try {
                    connection.send(messageFactory.event(
                            MessageType.PING,
                            new HeartbeatPayload(System.currentTimeMillis())
                    ));
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }
        };

        try (TcpServer server = new TcpServer(
                0,
                2,
                (connection, message) -> {
                    if (message.type() == MessageType.PONG) {
                        pongReceived.countDown();
                    }
                },
                listener
        )) {
            server.start();
            try (Socket socket = new Socket("127.0.0.1", server.port())) {
                MessageIO messageIO = new MessageIO();
                MessageEnvelope ping = messageIO.read(socket.getInputStream());
                assertEquals(MessageType.PING, ping.type());
                messageIO.write(socket.getOutputStream(), new MessageEnvelope(
                        MessageType.PONG,
                        ping.requestId(),
                        null,
                        null,
                        ping.data(),
                        null
                ));
                assertTrue(pongReceived.await(2, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void acceptsMultipleClientsConcurrently() throws Exception {
        int clientCount = 8;
        CountDownLatch connected = new CountDownLatch(clientCount);
        ConnectionListener listener = new ConnectionListener() {
            @Override
            public void onConnected(ClientConnection connection) {
                connected.countDown();
            }
        };

        try (TcpServer server = new TcpServer(0, clientCount, (connection, message) -> { }, listener)) {
            server.start();
            List<Socket> sockets = new ArrayList<>();
            try {
                for (int index = 0; index < clientCount; index++) {
                    sockets.add(new Socket("127.0.0.1", server.port()));
                }
                assertTrue(connected.await(2, TimeUnit.SECONDS));
                assertEquals(clientCount, server.connectionCount());
            } finally {
                for (Socket socket : sockets) {
                    socket.close();
                }
            }
        }
    }

    @Test
    void containsUnexpectedDisconnectToOneConnection() throws Exception {
        CountDownLatch disconnected = new CountDownLatch(1);
        ConnectionListener listener = new ConnectionListener() {
            @Override
            public void onDisconnected(ClientConnection connection) {
                disconnected.countDown();
            }
        };

        try (TcpServer server = new TcpServer(0, 1, (connection, message) -> { }, listener)) {
            server.start();
            Socket socket = new Socket("127.0.0.1", server.port());
            socket.setSoLinger(true, 0);
            socket.close();

            assertTrue(disconnected.await(2, TimeUnit.SECONDS));
            assertTrue(waitUntil(() -> server.connectionCount() == 0, Duration.ofSeconds(2)));
            assertTrue(server.isRunning());
        }
    }

    @Test
    void closesOnlyTheConnectionThatSendsAnInvalidFrame() throws Exception {
        CountDownLatch invalidClientDisconnected = new CountDownLatch(1);
        CountDownLatch validMessageReceived = new CountDownLatch(1);
        ConnectionListener listener = new ConnectionListener() {
            @Override
            public void onDisconnected(ClientConnection connection) {
                invalidClientDisconnected.countDown();
            }
        };

        try (TcpServer server = new TcpServer(
                0,
                2,
                (connection, message) -> validMessageReceived.countDown(),
                listener
        )) {
            server.start();
            try (Socket invalidClient = new Socket("127.0.0.1", server.port())) {
                new DataOutputStream(invalidClient.getOutputStream()).writeInt(Integer.MAX_VALUE);
                assertTrue(invalidClientDisconnected.await(2, TimeUnit.SECONDS));
            }

            MessageFactory messageFactory = new MessageFactory(new JsonMessageCodec().objectMapper());
            try (Socket validClient = new Socket("127.0.0.1", server.port())) {
                new MessageIO().write(
                        validClient.getOutputStream(),
                        messageFactory.event(MessageType.PONG, new HeartbeatPayload(System.currentTimeMillis()))
                );
                assertTrue(validMessageReceived.await(2, TimeUnit.SECONDS));
                assertTrue(server.isRunning());
            }
        }
    }

    @Test
    void closesConnectionsForZeroAndExplicitlyOversizedFrames() throws Exception {
        CountDownLatch disconnected = new CountDownLatch(2);
        ConnectionListener listener = new ConnectionListener() {
            @Override
            public void onDisconnected(ClientConnection connection) {
                disconnected.countDown();
            }
        };

        try (TcpServer server = new TcpServer(0, 2, (connection, message) -> { }, listener)) {
            server.start();
            sendLengthOnly(server.port(), 0);
            sendLengthOnly(server.port(), ProtocolConstants.MAX_FRAME_LENGTH + 1);

            assertTrue(disconnected.await(2, TimeUnit.SECONDS));
            assertTrue(server.isRunning());
        }
    }

    @Test
    void malformedJsonClosesOnlyTheViolatingConnection() throws Exception {
        CountDownLatch disconnected = new CountDownLatch(1);
        CountDownLatch validMessageReceived = new CountDownLatch(1);
        ConnectionListener listener = new ConnectionListener() {
            @Override
            public void onDisconnected(ClientConnection connection) {
                disconnected.countDown();
            }
        };

        try (TcpServer server = new TcpServer(
                0,
                2,
                (connection, message) -> validMessageReceived.countDown(),
                listener
        )) {
            server.start();
            byte[] malformed = "{not-valid-json".getBytes(StandardCharsets.UTF_8);
            try (Socket socket = new Socket("127.0.0.1", server.port())) {
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                output.writeInt(malformed.length);
                output.write(malformed);
                output.flush();
                assertTrue(disconnected.await(2, TimeUnit.SECONDS));
            }

            sendValidHeartbeat(server.port());
            assertTrue(validMessageReceived.await(2, TimeUnit.SECONDS));
            assertTrue(server.isRunning());
        }
    }

    @Test
    void partialFrameDisconnectIsContainedAndServerKeepsServing() throws Exception {
        CountDownLatch disconnected = new CountDownLatch(1);
        CountDownLatch validMessageReceived = new CountDownLatch(1);
        ConnectionListener listener = new ConnectionListener() {
            @Override
            public void onDisconnected(ClientConnection connection) {
                disconnected.countDown();
            }
        };

        try (TcpServer server = new TcpServer(
                0,
                2,
                (connection, message) -> validMessageReceived.countDown(),
                listener
        )) {
            server.start();
            Socket partialClient = new Socket("127.0.0.1", server.port());
            DataOutputStream output = new DataOutputStream(partialClient.getOutputStream());
            output.writeInt(32);
            output.write(new byte[]{1, 2, 3});
            output.flush();
            partialClient.close();

            assertTrue(disconnected.await(2, TimeUnit.SECONDS));
            sendValidHeartbeat(server.port());
            assertTrue(validMessageReceived.await(2, TimeUnit.SECONDS));
            assertTrue(server.isRunning());
        }
    }

    private static void sendLengthOnly(int port, int length) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeInt(length);
            output.flush();
        }
    }

    private static void sendValidHeartbeat(int port) throws Exception {
        MessageFactory messageFactory = new MessageFactory(new JsonMessageCodec().objectMapper());
        try (Socket socket = new Socket("127.0.0.1", port)) {
            new MessageIO().write(
                    socket.getOutputStream(),
                    messageFactory.event(MessageType.PONG, new HeartbeatPayload(System.currentTimeMillis()))
            );
        }
    }

    private static boolean waitUntil(java.util.function.BooleanSupplier condition, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }
}
