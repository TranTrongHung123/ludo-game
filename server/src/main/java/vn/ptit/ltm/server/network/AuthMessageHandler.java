package vn.ptit.ltm.server.network;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.ptit.ltm.common.dto.EmptyPayload;
import vn.ptit.ltm.common.dto.auth.LoginRequest;
import vn.ptit.ltm.common.dto.auth.RegisterRequest;
import vn.ptit.ltm.common.dto.room.CreateRoomRequest;
import vn.ptit.ltm.common.dto.room.JoinRoomRequest;
import vn.ptit.ltm.common.dto.room.LeaveRoomRequest;
import vn.ptit.ltm.common.dto.room.RoomPayload;
import vn.ptit.ltm.common.dto.room.InvitePlayerRequest;
import vn.ptit.ltm.common.dto.room.InviteDecisionRequest;
import vn.ptit.ltm.common.dto.room.SetReadyRequest;
import vn.ptit.ltm.common.dto.room.StartGameRequest;
import vn.ptit.ltm.common.dto.game.MovePieceRequest;
import vn.ptit.ltm.common.dto.game.RollDiceRequest;
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
import vn.ptit.ltm.server.service.ServiceException;
import vn.ptit.ltm.server.lobby.LobbyService;
import vn.ptit.ltm.server.room.RoomService;
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
    private final RoomService roomService;
    private final PayloadMapper payloadMapper;
    private final MessageFactory messageFactory;

    public AuthMessageHandler(
            AuthService authService,
            SessionManager sessionManager,
            SessionStateProvider sessionStateProvider,
            HeartbeatManager heartbeatManager
    ) {
        this(authService, sessionManager, sessionStateProvider, heartbeatManager, null, null);
    }

    public AuthMessageHandler(
            AuthService authService,
            SessionManager sessionManager,
            SessionStateProvider sessionStateProvider,
            HeartbeatManager heartbeatManager,
            LobbyService lobbyService
    ) {
        this(authService, sessionManager, sessionStateProvider, heartbeatManager, lobbyService, null);
    }

    public AuthMessageHandler(
            AuthService authService,
            SessionManager sessionManager,
            SessionStateProvider sessionStateProvider,
            HeartbeatManager heartbeatManager,
            LobbyService lobbyService,
            RoomService roomService
    ) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.sessionStateProvider = Objects.requireNonNull(sessionStateProvider, "sessionStateProvider");
        this.heartbeatManager = Objects.requireNonNull(heartbeatManager, "heartbeatManager");
        this.lobbyService = lobbyService;
        this.roomService = roomService;
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
                case CREATE_ROOM -> handleCreateRoom(connection, message);
                case JOIN_ROOM -> handleJoinRoom(connection, message);
                case LEAVE_ROOM -> handleLeaveRoom(connection, message);
                case INVITE_PLAYER -> handleInvitePlayer(connection, message);
                case ACCEPT_INVITE -> handleAcceptInvite(connection, message);
                case REJECT_INVITE -> handleRejectInvite(connection, message);
                case READY -> handleSetReady(connection, message, true);
                case UNREADY -> handleSetReady(connection, message, false);
                case START_GAME -> handleStartGame(connection, message);
                case ROLL_DICE -> handleRollDice(connection, message);
                case MOVE_PIECE -> handleMovePiece(connection, message);
                default -> throw new AuthException(
                        ErrorCode.INVALID_REQUEST,
                        "Message type is not supported yet: " + message.type()
                );
            }
        } catch (ServiceException exception) {
            if (exception.errorCode() == ErrorCode.INTERNAL_SERVER_ERROR) {
                LOGGER.error(
                        "Request {} ({}) failed internally for connection {}",
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
        PlayerSession session = sessionManager.requireAuthenticated(message.sessionId(), connection.id());
        if (roomService != null) {
            roomService.leaveForSessionEnd(session.user().id());
        }
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

    private void handleCreateRoom(ClientConnection connection, MessageEnvelope message)
            throws IOException {
        requireRoomService();
        payloadMapper.fromTree(message.data(), CreateRoomRequest.class);
        var room = roomService.createRoom(message.sessionId(), connection.id());
        connection.send(messageFactory.response(
                MessageType.CREATE_ROOM,
                message.requestId(),
                new RoomPayload(room)
        ));
        roomService.broadcastRoom(room.roomId());
    }

    private void handleJoinRoom(ClientConnection connection, MessageEnvelope message)
            throws IOException {
        requireRoomService();
        JoinRoomRequest request = payloadMapper.fromTree(message.data(), JoinRoomRequest.class);
        var room = roomService.joinRoom(message.sessionId(), connection.id(), request.roomId());
        connection.send(messageFactory.response(
                MessageType.JOIN_ROOM,
                message.requestId(),
                new RoomPayload(room)
        ));
        roomService.broadcastRoom(room.roomId());
    }

    private void handleLeaveRoom(ClientConnection connection, MessageEnvelope message)
            throws IOException {
        requireRoomService();
        LeaveRoomRequest request = payloadMapper.fromTree(message.data(), LeaveRoomRequest.class);
        roomService.leaveRoom(message.sessionId(), connection.id(), request.roomId());
        connection.send(messageFactory.response(
                MessageType.LEAVE_ROOM,
                message.requestId(),
                EmptyPayload.INSTANCE
        ));
        roomService.broadcastRoom(request.roomId());
    }

    private void handleInvitePlayer(ClientConnection connection, MessageEnvelope message)
            throws IOException {
        requireRoomService();
        InvitePlayerRequest request = payloadMapper.fromTree(message.data(), InvitePlayerRequest.class);
        var invitation = roomService.invitePlayer(
                message.sessionId(),
                connection.id(),
                request.roomId(),
                request.playerId()
        );
        roomService.deliverInvitation(invitation);
        connection.send(messageFactory.response(
                MessageType.INVITE_PLAYER,
                message.requestId(),
                invitation
        ));
    }

    private void handleAcceptInvite(ClientConnection connection, MessageEnvelope message)
            throws IOException {
        requireRoomService();
        InviteDecisionRequest request = payloadMapper.fromTree(message.data(), InviteDecisionRequest.class);
        var room = roomService.acceptInvitation(
                message.sessionId(),
                connection.id(),
                request.invitationId()
        );
        connection.send(messageFactory.response(
                MessageType.ACCEPT_INVITE,
                message.requestId(),
                new RoomPayload(room)
        ));
        roomService.broadcastRoom(room.roomId());
    }

    private void handleRejectInvite(ClientConnection connection, MessageEnvelope message)
            throws IOException {
        requireRoomService();
        InviteDecisionRequest request = payloadMapper.fromTree(message.data(), InviteDecisionRequest.class);
        roomService.rejectInvitation(
                message.sessionId(),
                connection.id(),
                request.invitationId()
        );
        connection.send(messageFactory.response(
                MessageType.REJECT_INVITE,
                message.requestId(),
                EmptyPayload.INSTANCE
        ));
    }

    private void handleSetReady(
            ClientConnection connection,
            MessageEnvelope message,
            boolean expectedReady
    ) throws IOException {
        requireRoomService();
        SetReadyRequest request = payloadMapper.fromTree(message.data(), SetReadyRequest.class);
        if (request.ready() != expectedReady) {
            throw new AuthException(ErrorCode.INVALID_REQUEST, "Ready value does not match message type");
        }
        var room = roomService.setReady(
                message.sessionId(),
                connection.id(),
                request.roomId(),
                expectedReady
        );
        connection.send(messageFactory.response(
                message.type(),
                message.requestId(),
                new RoomPayload(room)
        ));
        roomService.broadcastRoom(room.roomId());
    }

    private void handleStartGame(ClientConnection connection, MessageEnvelope message)
            throws IOException {
        requireRoomService();
        StartGameRequest request = payloadMapper.fromTree(message.data(), StartGameRequest.class);
        var gameState = roomService.startGame(
                message.sessionId(),
                connection.id(),
                request.roomId()
        );
        connection.send(messageFactory.response(
                MessageType.START_GAME,
                message.requestId(),
                gameState
        ));
        roomService.broadcastRoom(request.roomId());
        roomService.broadcastGame(request.roomId());
    }

    private void handleRollDice(ClientConnection connection, MessageEnvelope message)
            throws IOException {
        requireRoomService();
        RollDiceRequest request = payloadMapper.fromTree(message.data(), RollDiceRequest.class);
        var result = roomService.rollDice(
                message.sessionId(),
                connection.id(),
                request.roomId()
        );
        try {
            connection.send(messageFactory.response(
                    MessageType.DICE_RESULT,
                    message.requestId(),
                    result
            ));
        } finally {
            roomService.broadcastDiceResult(request.roomId(), result);
            roomService.broadcastGameUpdated(request.roomId());
        }
    }

    private void handleMovePiece(ClientConnection connection, MessageEnvelope message)
            throws IOException {
        requireRoomService();
        MovePieceRequest request = payloadMapper.fromTree(message.data(), MovePieceRequest.class);
        var result = roomService.movePiece(
                message.sessionId(),
                connection.id(),
                request.roomId(),
                request.pieceId()
        );
        try {
            connection.send(messageFactory.response(
                    MessageType.MOVE_PIECE_RESULT,
                    message.requestId(),
                    result
            ));
        } finally {
            roomService.broadcastGameUpdated(request.roomId());
        }
    }

    private void requireRoomService() {
        if (roomService == null) {
            throw new AuthException(ErrorCode.INTERNAL_SERVER_ERROR, "Room service is unavailable");
        }
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
