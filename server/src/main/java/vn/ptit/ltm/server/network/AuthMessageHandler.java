package vn.ptit.ltm.server.network;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.ptit.ltm.common.dto.EmptyPayload;
import vn.ptit.ltm.common.dto.auth.LoginRequest;
import vn.ptit.ltm.common.dto.auth.RegisterRequest;
import vn.ptit.ltm.common.dto.session.ReconnectRequest;
import vn.ptit.ltm.common.dto.session.ReconnectResult;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.common.protocol.JsonMessageCodec;
import vn.ptit.ltm.common.protocol.MessageEnvelope;
import vn.ptit.ltm.common.protocol.MessageFactory;
import vn.ptit.ltm.common.protocol.PayloadMapper;
import vn.ptit.ltm.server.service.AuthException;
import vn.ptit.ltm.server.service.AuthService;
import vn.ptit.ltm.server.lobby.LobbyService;
import vn.ptit.ltm.server.session.PlayerSession;
import vn.ptit.ltm.server.session.SessionManager;
import vn.ptit.ltm.server.session.SessionStateProvider;

import java.io.IOException;
import java.util.Objects;

public final class AuthMessageHandler implements MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthMessageHandler.class);

    private final AuthService authService;
    private final SessionManager sessionManager;
    private final SessionStateProvider sessionStateProvider;
    private final HeartbeatManager heartbeatManager;
    private final LobbyService lobbyService;
    private final PayloadMapper payloadMapper;
    private final MessageFactory messageFactory;

    public AuthMessageHandler(
            AuthService authService,
            SessionManager sessionManager,
            SessionStateProvider sessionStateProvider,
            HeartbeatManager heartbeatManager
    ) {
        this(authService, sessionManager, sessionStateProvider, heartbeatManager, null);
    }

    public AuthMessageHandler(
            AuthService authService,
            SessionManager sessionManager,
            SessionStateProvider sessionStateProvider,
            HeartbeatManager heartbeatManager,
            LobbyService lobbyService
    ) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.sessionStateProvider = Objects.requireNonNull(sessionStateProvider, "sessionStateProvider");
        this.heartbeatManager = Objects.requireNonNull(heartbeatManager, "heartbeatManager");
        this.lobbyService = lobbyService;
        JsonMessageCodec codec = new JsonMessageCodec();
        this.payloadMapper = new PayloadMapper(codec.objectMapper());
        this.messageFactory = new MessageFactory(codec.objectMapper());
    }

    @Override
    public void handle(ClientConnection connection, MessageEnvelope message) throws IOException {
        if (message.type() == MessageType.PONG) {
            heartbeatManager.recordPong(connection);
            return;
        }
        if (message.type() == MessageType.PING) {
            connection.send(new MessageEnvelope(
                    MessageType.PONG,
                    message.requestId(),
                    null,
                    null,
                    message.data(),
                    null
            ));
            return;
        }

        try {
            requireRequestId(message.requestId());
            switch (message.type()) {
                case REGISTER -> handleRegister(connection, message);
                case LOGIN -> handleLogin(connection, message);
                case LOGOUT -> handleLogout(connection, message);
                case RECONNECT -> handleReconnect(connection, message);
                case GET_ONLINE_PLAYERS -> handleGetOnlinePlayers(connection, message);
                default -> throw new AuthException(
                        ErrorCode.INVALID_REQUEST,
                        "Message type is not supported yet: " + message.type()
                );
            }
        } catch (AuthException exception) {
            if (exception.errorCode() == ErrorCode.INTERNAL_SERVER_ERROR) {
                LOGGER.error(
                        "Authentication request {} ({}) failed internally for connection {}",
                        message.requestId(),
                        message.type(),
                        connection.id(),
                        exception
                );
            }
            connection.send(messageFactory.error(
                    message.requestId(),
                    exception.errorCode(),
                    exception.getMessage()
            ));
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException exception) {
            connection.send(messageFactory.error(
                    message.requestId(),
                    ErrorCode.INVALID_REQUEST,
                    "Invalid request payload"
            ));
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Request {} ({}) failed unexpectedly for connection {}",
                    message.requestId(),
                    message.type(),
                    connection.id(),
                    exception
            );
            connection.send(messageFactory.error(
                    message.requestId(),
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "Internal server error"
            ));
        }
    }

    private void handleRegister(ClientConnection connection, MessageEnvelope message)
            throws IOException {
        requireUnauthenticatedConnection(connection);
        RegisterRequest request = payloadMapper.fromTree(message.data(), RegisterRequest.class);
        connection.send(messageFactory.response(
                MessageType.REGISTER,
                message.requestId(),
                authService.register(request)
        ));
    }

    private void handleLogin(ClientConnection connection, MessageEnvelope message)
            throws IOException {
        LoginRequest request = payloadMapper.fromTree(message.data(), LoginRequest.class);
        connection.send(messageFactory.response(
                MessageType.LOGIN,
                message.requestId(),
                authService.login(request, connection.id())
        ));
    }

    private void handleLogout(ClientConnection connection, MessageEnvelope message) throws IOException {
        sessionManager.logout(message.sessionId(), connection.id());
        connection.send(messageFactory.response(
                MessageType.LOGOUT,
                message.requestId(),
                EmptyPayload.INSTANCE
        ));
    }

    private void handleReconnect(ClientConnection connection, MessageEnvelope message)
            throws IOException {
        ReconnectRequest request = payloadMapper.fromTree(message.data(), ReconnectRequest.class);
        PlayerSession session = sessionManager.reconnect(request.sessionId(), connection.id());
        ReconnectResult result = sessionStateProvider.restore(session);
        connection.send(messageFactory.response(
                MessageType.RECONNECT_RESULT,
                message.requestId(),
                result
        ));
    }

    private void handleGetOnlinePlayers(ClientConnection connection, MessageEnvelope message)
            throws IOException {
        sessionManager.requireAuthenticated(message.sessionId(), connection.id());
        if (lobbyService == null) {
            throw new AuthException(ErrorCode.INTERNAL_SERVER_ERROR, "Lobby service is unavailable");
        }
        connection.send(messageFactory.response(
                MessageType.ONLINE_PLAYERS_UPDATED,
                message.requestId(),
                lobbyService.onlinePlayers()
        ));
    }

    private void requireUnauthenticatedConnection(ClientConnection connection) {
        if (sessionManager.findByConnectionId(connection.id()).isPresent()) {
            throw new AuthException(ErrorCode.INVALID_REQUEST, "Connection is already authenticated");
        }
    }

    private static void requireRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new AuthException(ErrorCode.INVALID_REQUEST, "requestId is required");
        }
    }
}
