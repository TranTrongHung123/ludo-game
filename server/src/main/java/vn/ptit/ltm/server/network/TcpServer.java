package vn.ptit.ltm.server.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.ptit.ltm.common.protocol.MessageIO;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class TcpServer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TcpServer.class);
    private static final int DEFAULT_BACKLOG = 50;

    private final int requestedPort;
    private final MessageHandler messageHandler;
    private final ConnectionListener connectionListener;
    private final ExecutorService acceptExecutor;
    private final ExecutorService clientExecutor;
    private final Set<ClientConnection> connections = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private volatile ServerSocket serverSocket;

    public TcpServer(
            int port,
            int workerThreads,
            MessageHandler messageHandler,
            ConnectionListener connectionListener
    ) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        if (workerThreads <= 0) {
            throw new IllegalArgumentException("workerThreads must be positive");
        }
        this.requestedPort = port;
        this.messageHandler = java.util.Objects.requireNonNull(messageHandler, "messageHandler");
        this.connectionListener = java.util.Objects.requireNonNull(connectionListener, "connectionListener");
        this.acceptExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("tcp-accept"));
        this.clientExecutor = Executors.newFixedThreadPool(workerThreads, new NamedThreadFactory("tcp-client"));
    }

    public synchronized void start() throws IOException {
        if (closed.get()) {
            throw new IllegalStateException("Server is already closed");
        }
        if (running.get()) {
            throw new IllegalStateException("Server is already running");
        }
        ServerSocket socket = new ServerSocket();
        try {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(requestedPort), DEFAULT_BACKLOG);
        } catch (IOException exception) {
            socket.close();
            throw exception;
        }
        serverSocket = socket;
        running.set(true);
        acceptExecutor.execute(this::acceptLoop);
        LOGGER.info("TCP server listening on port {}", port());
    }

    public boolean isRunning() {
        return running.get();
    }

    public int port() {
        ServerSocket socket = serverSocket;
        if (socket == null || !socket.isBound()) {
            throw new IllegalStateException("Server has not started");
        }
        return socket.getLocalPort();
    }

    public int connectionCount() {
        return connections.size();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                submit(socket);
            } catch (SocketException exception) {
                if (running.get()) {
                    LOGGER.error("TCP accept loop stopped unexpectedly", exception);
                }
                break;
            } catch (IOException exception) {
                if (running.get()) {
                    LOGGER.error("Unable to accept client connection", exception);
                }
            }
        }
    }

    private void submit(Socket socket) {
        ClientConnection connection = new ClientConnection(socket, new MessageIO());
        connections.add(connection);
        ConnectionListener trackingListener = new ConnectionListener() {
            @Override
            public void onConnected(ClientConnection connected) {
                connectionListener.onConnected(connected);
            }

            @Override
            public void onDisconnected(ClientConnection disconnected) {
                connections.remove(disconnected);
                connectionListener.onDisconnected(disconnected);
            }
        };
        try {
            clientExecutor.execute(new ClientHandler(connection, messageHandler, trackingListener));
        } catch (RejectedExecutionException exception) {
            connections.remove(connection);
            connection.close();
            if (running.get()) {
                LOGGER.warn("Rejected client connection because the worker pool is unavailable");
            }
        }
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        running.set(false);
        ServerSocket socket = serverSocket;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException exception) {
                LOGGER.warn("Unable to close server socket cleanly", exception);
            }
        }
        connections.forEach(ClientConnection::close);
        connections.clear();
        acceptExecutor.shutdownNow();
        clientExecutor.shutdownNow();
        awaitTermination(acceptExecutor);
        awaitTermination(clientExecutor);
        LOGGER.info("TCP server stopped");
    }

    private static void awaitTermination(ExecutorService executor) {
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("Network executor did not stop within the timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class NamedThreadFactory implements java.util.concurrent.ThreadFactory {
        private final String prefix;
        private final AtomicInteger sequence = new AtomicInteger();

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, prefix + "-" + sequence.incrementAndGet());
        }
    }
}
