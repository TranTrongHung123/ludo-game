package vn.ptit.ltm.server.network;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.ptit.ltm.common.dto.EmptyPayload;
import vn.ptit.ltm.common.dto.auth.LoginRequest;
import vn.ptit.ltm.common.dto.auth.RegisterRequest;
import vn.ptit.ltm.common.dto.chat.ChatMessageDto;
import vn.ptit.ltm.common.dto.chat.SendChatMessageRequest;
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
import vn.ptit.ltm.server.service.MatchService;
import vn.ptit.ltm.server.service.RankingService;
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
    private final MatchService matchService;
    private final RankingService rankingService;
    private final PayloadMapper payloadMapper;
    private final MessageFactory messageFactory;
    private final RequestIdRegistry requestIds = new RequestIdRegistry();

    public AuthMessageHandler(
            AuthService authService,
            SessionManager sessionManager,
            SessionStateProvider sessionStateProvider,
            HeartbeatManager heartbeatManager
    ) {
        this(authService, sessionManager, sessionStateProvider, heartbeatManager, null, null, null, null);
    }

    public AuthMessageHandler(
            AuthService authService,
            SessionManager sessionManager,
            SessionStateProvider sessionStateProvider,
            HeartbeatManager heartbeatManager,
            LobbyService lobbyService
    ) {
        this(authService, sessionManager, sessionStateProvider, heartbeatManager, lobbyService, null, null, null);
    }

    public AuthMessageHandler(
            AuthService authService,
            SessionManager sessionManager,
            SessionStateProvider sessionStateProvider,
            HeartbeatManager heartbeatManager,
            LobbyService lobbyService,
            RoomService roomService
    ) {
        this(
                authService,
                sessionManager,
                sessionStateProvider,
                heartbeatManager,
                lobbyService,
                roomService,
                null,
                null
        );
    }

    public AuthMessageHandler(
            AuthService authService,
            SessionManager sessionManager,
            SessionStateProvider sessionStateProvider,
            HeartbeatManager heartbeatManager,
            LobbyService lobbyService,
            RoomService roomService,
            MatchService matchService,
            RankingService rankingService
    ) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.sessionStateProvider = Objects.requireNonNull(sessionStateProvider, "sessionStateProvider");
        this.heartbeatManager = Objects.requireNonNull(heartbeatManager, "heartbeatManager");
        this.lobbyService = lobbyService;
        this.roomService = roomService;
        this.matchService = matchService;
        this.rankingService = rankingService;
        JsonMessageCodec codec = new JsonMessageCodec();
        this.payloadMapper = new PayloadMapper(codec.objectMapper());
        this.messageFactory = new MessageFactory(codec.objectMapper());
    }

    // Kiểm tra envelope, chặn request lặp, dispatch nghiệp vụ và ánh xạ lỗi thành response.
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
            if (!requestIds.register(connection.id(), message.requestId())) {
                throw new AuthException(
                        ErrorCode.INVALID_REQUEST,
                        "Duplicate requestId"
                );
            }
            switch (message.type()) {
                case REGISTER -> handleRegister(connection, message);
                case LOGIN -> handleLogin(connection, message);
                case LOGOUT -> handleLogout(connection, message);
                case RECONNECT -> handleReconnect(connection, message);
                case GET_ONLINE_PLAYERS -> handleGetOnlinePlayers(connection, message);
                case GET_RANKING -> handleGetRanking(connection, message);
                case GET_MATCH_HISTORY -> handleGetMatchHistory(connection, message);
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
                case CHAT_MESSAGE -> handleChatMessage(connection, message);
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

    // Đọc payload đăng ký và trả hồ sơ mới mà không tạo phiên đăng nhập.
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

    // Xác thực tài khoản rồi trả token và hồ sơ qua connection gửi yêu cầu.
    private void handleLogin(ClientConnection connection, MessageEnvelope message)
            throws IOException {
        LoginRequest request = payloadMapper.fromTree(message.data(), LoginRequest.class);
        connection.send(messageFactory.response(
                MessageType.LOGIN,
                message.requestId(),
                authService.login(request, connection.id())
        ));
    }

    // Xử lý rời phòng và hủy phiên khi người dùng đăng xuất.
    private void handleLogout(ClientConnection connection, MessageEnvelope message) throws IOException {
        PlayerSession session = sessionManager.requireAuthenticated(message.sessionId(), connection.id());
        sessionManager.logout(message.sessionId(), connection.id());
        if (roomService != null) {
            roomService.leaveForSessionEnd(session.user().id());
        }
        connection.send(messageFactory.response(
                MessageType.LOGOUT,
                message.requestId(),
                EmptyPayload.INSTANCE
        ));
    }

    // Gắn lại phiên và trả snapshot khôi phục từ dịch vụ phòng.
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

    // Xác thực phiên rồi trả danh sách người chơi trực tuyến.
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

    // Trả bảng xếp hạng từ dữ liệu đã lưu cho người đã đăng nhập.
    private void handleGetRanking(ClientConnection connection, MessageEnvelope message)
            throws IOException {
        sessionManager.requireAuthenticated(message.sessionId(), connection.id());
        payloadMapper.fromTree(message.data(), EmptyPayload.class);
        if (rankingService == null) {
            throw new AuthException(ErrorCode.INTERNAL_SERVER_ERROR, "Ranking service is unavailable");
        }
        connection.send(messageFactory.response(
                MessageType.RANKING_RESULT,
                message.requestId(),
                rankingService.ranking()
        ));
    }

    // Lấy lịch sử của tài khoản trong phiên, không nhận userId tùy ý từ client.
    private void handleGetMatchHistory(ClientConnection connection, MessageEnvelope message)
            throws IOException {
        PlayerSession session = sessionManager.requireAuthenticated(message.sessionId(), connection.id());
        payloadMapper.fromTree(message.data(), EmptyPayload.class);
        if (matchService == null) {
            throw new AuthException(ErrorCode.INTERNAL_SERVER_ERROR, "Match service is unavailable");
        }
        connection.send(messageFactory.response(
                MessageType.MATCH_HISTORY_RESULT,
                message.requestId(),
                matchService.matchHistory(session.user().id())
        ));
    }

    // Tạo phòng cho phiên hiện tại rồi trả và broadcast trạng thái phòng.
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

    // Ánh xạ mã phòng từ payload và thông báo membership mới.
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

    // Chuyển yêu cầu rời phòng qua dịch vụ để áp dụng đúng luật bỏ cuộc.
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
        roomService.broadcastGameUpdated(request.roomId());
    }

    // Tạo lời mời hợp lệ và gửi tới người nhận đang trực tuyến.
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

    // Tiêu thụ lời mời, tham gia phòng và thông báo trạng thái mới.
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

    // Xác nhận từ chối sau khi dịch vụ loại lời mời của đúng người nhận.
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

    // Cập nhật Ready từ yêu cầu và gửi trạng thái phòng cho các thành viên.
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

    // Yêu cầu dịch vụ khởi tạo trận rồi gửi snapshot tới phòng.
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

    // Lấy xúc xắc từ Server và gửi cùng kết quả cho người yêu cầu và phòng.
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

    // Chuyển ý định chọn quân tới dịch vụ và gửi snapshot sau nước đi.
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

    // Xử lý chat qua dịch vụ phòng trước khi phát tin nhắn cho thành viên.
    private void handleChatMessage(ClientConnection connection, MessageEnvelope message) throws IOException {
        requireRoomService();
        SendChatMessageRequest request = payloadMapper.fromTree(message.data(), SendChatMessageRequest.class);
        ChatMessageDto chatMessage = roomService.sendChatMessage(
                message.sessionId(),
                connection.id(),
                request.roomId(),
                request.message()
        );
        connection.send(messageFactory.response(
                MessageType.CHAT_MESSAGE,
                message.requestId(),
                chatMessage
        ));
        roomService.broadcastChatMessage(request.roomId(), chatMessage);
    }

    private void requireRoomService() {
        if (roomService == null) {
            throw new AuthException(ErrorCode.INTERNAL_SERVER_ERROR, "Room service is unavailable");
        }
    }

    // Chặn đăng ký hoặc đăng nhập mới trên connection đã có phiên.
    private void requireUnauthenticatedConnection(ClientConnection connection) {
        if (sessionManager.findByConnectionId(connection.id()).isPresent()) {
            throw new AuthException(ErrorCode.INVALID_REQUEST, "Connection is already authenticated");
        }
    }

    // Yêu cầu mã tương quan để ghép response và phát hiện gửi lặp.
    private static void requireRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new AuthException(ErrorCode.INVALID_REQUEST, "requestId is required");
        }
    }
}
