package vn.ptit.ltm.client.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.error.ProtocolException;
import vn.ptit.ltm.common.protocol.MessageEnvelope;
import vn.ptit.ltm.common.protocol.MessageIO;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TcpClient implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpClient.class);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final ClientMessageListener listener;
    private final MessageIO messageIO;
    private final ExecutorService listenerExecutor;
    private final Object sendLock = new Object();
    private final AtomicBoolean connected = new AtomicBoolean();
    private final AtomicBoolean disconnectNotified = new AtomicBoolean();

    private volatile Socket socket;

    public TcpClient(ClientMessageListener listener) {
        this(listener, new MessageIO());
    }

    TcpClient(ClientMessageListener listener, MessageIO messageIO) {
        this.listener = Objects.requireNonNull(listener, "listener");
        this.messageIO = Objects.requireNonNull(messageIO, "messageIO");
        this.listenerExecutor = Executors.newSingleThreadExecutor(runnable ->
                new Thread(runnable, "tcp-client-listener"));
    }

    public synchronized void connect(String host, int port) throws IOException {
        connect(host, port, DEFAULT_CONNECT_TIMEOUT);
    }

    public synchronized void connect(String host, int port, Duration timeout) throws IOException {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(timeout, "timeout");
        if (host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        if (timeout.isZero() || timeout.isNegative() || timeout.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("timeout must be a positive integer number of milliseconds");
        }
        if (connected.get()) {
            throw new IllegalStateException("Client is already connected");
        }

        Socket newSocket = new Socket();
        try {
            newSocket.connect(new InetSocketAddress(host, port), (int) timeout.toMillis());
            newSocket.setTcpNoDelay(true);
        } catch (IOException exception) {
            newSocket.close();
            throw exception;
        }
        socket = newSocket;
        disconnectNotified.set(false);
        connected.set(true);
        listenerExecutor.execute(this::listenLoop);
    }

    public boolean isConnected() {
        Socket currentSocket = socket;
        return connected.get() && currentSocket != null && !currentSocket.isClosed();
    }

    public void send(MessageEnvelope message) throws IOException {
        Objects.requireNonNull(message, "message");
        Socket currentSocket = socket;
        if (!isConnected() || currentSocket == null) {
            throw new IOException("Client is not connected");
        }
        synchronized (sendLock) {
            messageIO.write(currentSocket.getOutputStream(), message);
        }
    }

    private void listenLoop() {
        Throwable disconnectCause = null;
        try {
            while (isConnected()) {
                MessageEnvelope message = messageIO.read(socket.getInputStream());
                if (message.type() == MessageType.PING) {
                    send(new MessageEnvelope(
                            MessageType.PONG,
                            message.requestId(),
                            null,
                            null,
                            message.data(),
                            null
                    ));
                }
                notifyMessage(message);
            }
        } catch (EOFException | SocketException exception) {
            disconnectCause = exception;
            LOGGER.debug("Disconnected from server: {}", exception.getMessage());
        } catch (ProtocolException exception) {
            disconnectCause = exception;
            LOGGER.warn("Server sent an invalid protocol message: {}", exception.getMessage());
        } catch (IOException exception) {
            disconnectCause = exception;
            if (connected.get()) {
                LOGGER.warn("Network listener stopped after an I/O failure: {}", exception.getMessage());
            }
        } finally {
            connected.set(false);
            closeSocket();
            notifyDisconnected(disconnectCause);
        }
    }

    private void notifyMessage(MessageEnvelope message) {
        try {
            listener.onMessage(message);
        } catch (RuntimeException exception) {
            LOGGER.error("Client message listener failed for message {}", message.type(), exception);
        }
    }

    private void notifyDisconnected(Throwable cause) {
        if (!disconnectNotified.compareAndSet(false, true)) {
            return;
        }
        try {
            listener.onDisconnected(cause);
        } catch (RuntimeException exception) {
            LOGGER.error("Client disconnect listener failed", exception);
        }
    }

    @Override
    public synchronized void close() {
        connected.set(false);
        closeSocket();
        listenerExecutor.shutdownNow();
    }

    private void closeSocket() {
        Socket currentSocket = socket;
        if (currentSocket == null) {
            return;
        }
        try {
            currentSocket.close();
        } catch (IOException exception) {
            LOGGER.debug("Unable to close client socket cleanly", exception);
        }
    }
}
