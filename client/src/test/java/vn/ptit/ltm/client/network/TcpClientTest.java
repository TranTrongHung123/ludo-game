package vn.ptit.ltm.client.network;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.dto.session.HeartbeatPayload;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.protocol.JsonMessageCodec;
import vn.ptit.ltm.common.protocol.MessageEnvelope;
import vn.ptit.ltm.common.protocol.MessageFactory;
import vn.ptit.ltm.common.protocol.MessageIO;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TcpClientTest {
    @Test
    void listensInBackgroundAndAutomaticallyRepliesToPing() throws Exception {
        MessageFactory messageFactory = new MessageFactory(new JsonMessageCodec().objectMapper());
        CountDownLatch pingDelivered = new CountDownLatch(1);
        AtomicReference<String> callbackThread = new AtomicReference<>();

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            CompletableFuture<MessageEnvelope> pongFuture = CompletableFuture.supplyAsync(() -> {
                try (Socket accepted = serverSocket.accept()) {
                    MessageIO messageIO = new MessageIO();
                    messageIO.write(accepted.getOutputStream(), messageFactory.event(
                            MessageType.PING,
                            new HeartbeatPayload(System.currentTimeMillis())
                    ));
                    return messageIO.read(accepted.getInputStream());
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });

            String testThread = Thread.currentThread().getName();
            try (TcpClient client = new TcpClient(message -> {
                callbackThread.set(Thread.currentThread().getName());
                if (message.type() == MessageType.PING) {
                    pingDelivered.countDown();
                }
            })) {
                client.connect("127.0.0.1", serverSocket.getLocalPort());
                assertTrue(pingDelivered.await(2, TimeUnit.SECONDS));
                assertEquals(MessageType.PONG, pongFuture.get(2, TimeUnit.SECONDS).type());
                assertNotEquals(testThread, callbackThread.get());
            }
        }
    }
}
