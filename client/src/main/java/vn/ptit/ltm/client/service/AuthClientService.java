package vn.ptit.ltm.client.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.ptit.ltm.client.config.ClientConfig;
import vn.ptit.ltm.client.network.ClientMessageListener;
import vn.ptit.ltm.client.network.ClientProtocolException;
import vn.ptit.ltm.client.network.ClientRequestException;
import vn.ptit.ltm.client.network.TcpClient;
import vn.ptit.ltm.client.state.ClientSessionState;
import vn.ptit.ltm.client.state.ConnectionState;
import vn.ptit.ltm.common.dto.EmptyPayload;
import vn.ptit.ltm.common.dto.auth.LoginRequest;
import vn.ptit.ltm.common.dto.auth.LoginResult;
import vn.ptit.ltm.common.dto.auth.RegisterRequest;
import vn.ptit.ltm.common.dto.auth.RegisterResult;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.common.protocol.JsonMessageCodec;
import vn.ptit.ltm.common.protocol.MessageEnvelope;
import vn.ptit.ltm.common.protocol.MessageFactory;
import vn.ptit.ltm.common.protocol.PayloadMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class AuthClientService implements AutoCloseable, ClientMessageListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthClientService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final ClientConfig config;
    private final ClientSessionState sessionState;
    private final TcpClient tcpClient;
    private final MessageFactory messageFactory;
    private final PayloadMapper payloadMapper;
    private final ExecutorService requestExecutor;
    private final ConcurrentHashMap<String, CompletableFuture<MessageEnvelope>> pendingRequests =
            new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<ConnectionState>> connectionListeners =
            new CopyOnWriteArrayList<>();
    private final Object connectionLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();

    private volatile ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private CompletableFuture<Void> connectionAttempt;

    public AuthClientService(ClientConfig config, ClientSessionState sessionState) {
        this.config = Objects.requireNonNull(config, "config");
        this.sessionState = Objects.requireNonNull(sessionState, "sessionState");
        JsonMessageCodec codec = new JsonMessageCodec();
        this.messageFactory = new MessageFactory(codec.objectMapper());
        this.payloadMapper = new PayloadMapper(codec.objectMapper());
        this.requestExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "client-request-sender");
            thread.setDaemon(true);
            return thread;
        });
        this.tcpClient = new TcpClient(this);
    }

    public CompletableFuture<Void> connectAsync() {
        synchronized (connectionLock) {
            ensureOpen();
            if (tcpClient.isConnected()) {
                return CompletableFuture.completedFuture(null);
            }
            if (connectionAttempt != null && !connectionAttempt.isDone()) {
                return connectionAttempt;
            }

            updateConnectionState(ConnectionState.CONNECTING);
            connectionAttempt = CompletableFuture.runAsync(() -> {
                try {
                    tcpClient.connect(config.serverHost(), config.serverPort());
                } catch (IOException exception) {
                    throw new CompletionException(exception);
                }
            }, requestExecutor).whenComplete((ignored, failure) -> {
                updateConnectionState(
                        failure == null ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED
                );
            });
            return connectionAttempt;
        }
    }

    public CompletableFuture<RegisterResult> register(
            String username,
            String password,
            String displayName
    ) {
        return request(
                MessageType.REGISTER,
                null,
                new RegisterRequest(username, password, displayName),
                MessageType.REGISTER,
                RegisterResult.class
        );
    }

    public CompletableFuture<LoginResult> login(String username, String password) {
        return request(
                MessageType.LOGIN,
                null,
                new LoginRequest(username, password),
                MessageType.LOGIN,
                LoginResult.class
        ).thenApply(result -> {
            sessionState.authenticate(result);
            return result;
        });
    }

    public CompletableFuture<Void> logout() {
        String sessionId;
        try {
            sessionId = sessionState.requireSessionId();
        } catch (IllegalStateException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        return request(
                MessageType.LOGOUT,
                sessionId,
                EmptyPayload.INSTANCE,
                MessageType.LOGOUT,
                EmptyPayload.class
        ).thenAccept(ignored -> sessionState.clear());
    }

    public ConnectionState connectionState() {
        return connectionState;
    }

    public boolean isConnected() {
        return tcpClient.isConnected();
    }

    public Runnable addConnectionStateListener(Consumer<ConnectionState> listener) {
        Consumer<ConnectionState> requiredListener = Objects.requireNonNull(listener, "listener");
        connectionListeners.add(requiredListener);
        return () -> connectionListeners.remove(requiredListener);
    }

    @Override
    public void onMessage(MessageEnvelope message) {
        String requestId = message.requestId();
        if (requestId == null) {
            return;
        }
        CompletableFuture<MessageEnvelope> pending = pendingRequests.get(requestId);
        if (pending != null) {
            pending.complete(message);
        }
    }

    @Override
    public void onDisconnected(Throwable cause) {
        if (closed.get()) {
            return;
        }
        updateConnectionState(ConnectionState.DISCONNECTED);
        IOException failure = new IOException("Connection to server was lost", cause);
        pendingRequests.values().forEach(pending -> pending.completeExceptionally(failure));
    }

    private <T> CompletableFuture<T> request(
            MessageType requestType,
            String sessionId,
            Object payload,
            MessageType responseType,
            Class<T> responseClass
    ) {
        Objects.requireNonNull(requestType, "requestType");
        Objects.requireNonNull(responseType, "responseType");
        Objects.requireNonNull(responseClass, "responseClass");
        if (!tcpClient.isConnected()) {
            return CompletableFuture.failedFuture(new IOException("Client is not connected to server"));
        }

        String requestId = UUID.randomUUID().toString();
        MessageEnvelope outbound = messageFactory.request(requestType, requestId, sessionId, payload);
        CompletableFuture<MessageEnvelope> responseFuture = new CompletableFuture<>();
        pendingRequests.put(requestId, responseFuture);

        CompletableFuture.runAsync(() -> {
            try {
                tcpClient.send(outbound);
            } catch (IOException exception) {
                responseFuture.completeExceptionally(exception);
            }
        }, requestExecutor);

        return responseFuture
                .orTimeout(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .thenApply(response -> decodeResponse(response, responseType, responseClass))
                .whenComplete((result, failure) -> pendingRequests.remove(requestId, responseFuture));
    }

    private <T> T decodeResponse(
            MessageEnvelope response,
            MessageType expectedType,
            Class<T> responseClass
    ) {
        if (response.type() == MessageType.ERROR) {
            if (response.error() == null) {
                throw new ClientProtocolException("Server returned an error without an error payload");
            }
            throw new ClientRequestException(response.error().code(), response.error().message());
        }
        if (response.type() != expectedType || !Boolean.TRUE.equals(response.success())) {
            throw new ClientProtocolException(
                    "Unexpected response type " + response.type() + " for " + expectedType
            );
        }
        try {
            return payloadMapper.fromTree(response.data(), responseClass);
        } catch (JsonProcessingException | NullPointerException exception) {
            throw new ClientProtocolException("Server returned an invalid response payload", exception);
        }
    }

    private void updateConnectionState(ConnectionState newState) {
        connectionState = newState;
        for (Consumer<ConnectionState> listener : connectionListeners) {
            try {
                listener.accept(newState);
            } catch (RuntimeException exception) {
                LOGGER.error("Connection state listener failed", exception);
            }
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Auth client service is closed");
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        IOException failure = new IOException("Auth client service is closed");
        pendingRequests.values().forEach(pending -> pending.completeExceptionally(failure));
        pendingRequests.clear();
        connectionListeners.clear();
        tcpClient.close();
        requestExecutor.shutdownNow();
        connectionState = ConnectionState.DISCONNECTED;
    }
}
