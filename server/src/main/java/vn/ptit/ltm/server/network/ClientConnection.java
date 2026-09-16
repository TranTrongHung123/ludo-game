package vn.ptit.ltm.server.network;

import vn.ptit.ltm.common.protocol.MessageEnvelope;
import vn.ptit.ltm.common.protocol.MessageIO;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClientConnection implements AutoCloseable {
    private final String id = UUID.randomUUID().toString();
    private final Socket socket;
    private final MessageIO messageIO;
    private final Object sendLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();

    ClientConnection(Socket socket, MessageIO messageIO) {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.messageIO = Objects.requireNonNull(messageIO, "messageIO");
    }

    public String id() {
        return id;
    }

    public SocketAddress remoteAddress() {
        return socket.getRemoteSocketAddress();
    }

    public boolean isOpen() {
        return !closed.get() && !socket.isClosed();
    }

    MessageEnvelope read() throws IOException {
        return messageIO.read(socket.getInputStream());
    }

    public void send(MessageEnvelope message) throws IOException {
        Objects.requireNonNull(message, "message");
        if (!isOpen()) {
            throw new IOException("Connection is closed");
        }
        synchronized (sendLock) {
            messageIO.write(socket.getOutputStream(), message);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // Socket close is best-effort; the connection is already unusable.
        }
    }
}
