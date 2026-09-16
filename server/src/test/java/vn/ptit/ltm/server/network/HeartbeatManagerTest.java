package vn.ptit.ltm.server.network;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.protocol.MessageEnvelope;
import vn.ptit.ltm.common.protocol.MessageIO;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartbeatManagerTest {
    @Test
    void keepsResponsiveClientAndClosesSilentClient() throws Exception {
        CountDownLatch silentClientDisconnected = new CountDownLatch(1);
        CountDownLatch responsivePongs = new CountDownLatch(3);
        try (HeartbeatManager heartbeat = new HeartbeatManager(Duration.ofMillis(100), 3);
             TcpServer server = new TcpServer(
                     0,
                     4,
                     (connection, message) -> {
                         if (message.type() == MessageType.PONG) {
                             heartbeat.recordPong(connection);
                             responsivePongs.countDown();
                         }
                     },
                     new CompositeConnectionListener(
                             heartbeat,
                             new ConnectionListener() {
                                 @Override
                                 public void onDisconnected(ClientConnection connection) {
                                     silentClientDisconnected.countDown();
                                 }
                             }
                     )
            )) {
            server.start();
            AtomicBoolean keepResponding = new AtomicBoolean(true);
            try (Socket responsive = new Socket("127.0.0.1", server.port());
                 Socket silent = new Socket("127.0.0.1", server.port())) {
                CompletableFuture<Void> responder = startResponder(responsive, keepResponding);

                assertTrue(silentClientDisconnected.await(3, TimeUnit.SECONDS));
                assertTrue(responsivePongs.await(1, TimeUnit.SECONDS));
                assertTrue(waitUntil(() -> server.connectionCount() == 1, Duration.ofSeconds(1)));
                keepResponding.set(false);
                responsive.close();
                responder.get(2, TimeUnit.SECONDS);
            }
        }
    }

    private static CompletableFuture<Void> startResponder(Socket socket, AtomicBoolean keepResponding) {
        return CompletableFuture.runAsync(() -> {
            MessageIO messageIO = new MessageIO();
            try {
                while (keepResponding.get()) {
                    MessageEnvelope ping = messageIO.read(socket.getInputStream());
                    if (ping.type() == MessageType.PING) {
                        messageIO.write(socket.getOutputStream(), new MessageEnvelope(
                                MessageType.PONG,
                                ping.requestId(),
                                null,
                                null,
                                ping.data(),
                                null
                        ));
                    }
                }
            } catch (IOException exception) {
                if (keepResponding.get()) {
                    throw new IllegalStateException(exception);
                }
            }
        });
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
