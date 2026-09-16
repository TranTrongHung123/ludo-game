package vn.ptit.ltm.server.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.ptit.ltm.common.error.ProtocolException;
import vn.ptit.ltm.common.protocol.MessageEnvelope;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.util.Objects;

public final class ClientHandler implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientHandler.class);

    private final ClientConnection connection;
    private final MessageHandler messageHandler;
    private final ConnectionListener connectionListener;

    public ClientHandler(
            ClientConnection connection,
            MessageHandler messageHandler,
            ConnectionListener connectionListener
    ) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler");
        this.connectionListener = Objects.requireNonNull(connectionListener, "connectionListener");
    }

    @Override
    public void run() {
        try {
            connectionListener.onConnected(connection);
            while (connection.isOpen()) {
                MessageEnvelope message = connection.read();
                messageHandler.handle(connection, message);
            }
        } catch (EOFException | SocketException exception) {
            LOGGER.debug("Client {} disconnected: {}", connection.id(), exception.getMessage());
        } catch (ProtocolException exception) {
            LOGGER.warn("Closing client {} after protocol violation: {}", connection.id(), exception.getMessage());
        } catch (IOException exception) {
            LOGGER.warn("I/O failure for client {}: {}", connection.id(), exception.getMessage());
        } catch (Exception exception) {
            LOGGER.error("Unhandled request failure for client {}", connection.id(), exception);
        } finally {
            connection.close();
            try {
                connectionListener.onDisconnected(connection);
            } catch (RuntimeException exception) {
                LOGGER.error("Disconnect listener failed for client {}", connection.id(), exception);
            }
        }
    }
}
