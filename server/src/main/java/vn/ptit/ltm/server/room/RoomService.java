package vn.ptit.ltm.server.room;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.ptit.ltm.common.dto.room.RoomDto;
import vn.ptit.ltm.common.dto.room.RoomPayload;
import vn.ptit.ltm.common.dto.room.InvitationDto;
import vn.ptit.ltm.common.dto.game.GameStateDto;
import vn.ptit.ltm.common.dto.game.DiceResultDto;
import vn.ptit.ltm.common.dto.game.MovePieceResultDto;
import vn.ptit.ltm.common.dto.game.TurnTimeoutDto;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.enums.PlayerPresenceState;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.common.model.GameConstants;
import vn.ptit.ltm.common.protocol.JsonMessageCodec;
import vn.ptit.ltm.common.protocol.MessageEnvelope;
import vn.ptit.ltm.common.protocol.MessageFactory;
import vn.ptit.ltm.server.network.ClientConnection;
import vn.ptit.ltm.server.network.ConnectionRegistry;
import vn.ptit.ltm.server.game.DiceRoller;
import vn.ptit.ltm.server.session.PlayerSession;
import vn.ptit.ltm.server.session.SessionManager;
import vn.ptit.ltm.server.session.SessionSnapshot;
import vn.ptit.ltm.server.service.MatchService;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RoomService implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomService.class);

    private final SessionManager sessionManager;
    private final ConnectionRegistry connectionRegistry;
    private final RoomManager roomManager = new RoomManager();
    private final MessageFactory messageFactory;
    private final Clock clock;
    private final Duration invitationTtl;
    private final DiceRoller diceRoller;
    private final TimeoutManager timeoutManager;
    private final MatchService matchService;
    private final Object invitationLock = new Object();
    private final Map<String, PendingInvitation> invitationsById = new HashMap<>();
    private final Map<Long, String> invitationIdByTargetUserId = new HashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Map<Long, PlayerPresenceState> previousPresence;

    public RoomService(SessionManager sessionManager, ConnectionRegistry connectionRegistry) {
        this(
                sessionManager,
                connectionRegistry,
                Clock.systemUTC(),
                Duration.ofMillis(GameConstants.INVITATION_TTL_MILLIS),
                DiceRoller.random(),
                null
        );
    }

    public RoomService(
            SessionManager sessionManager,
            ConnectionRegistry connectionRegistry,
            MatchService matchService
    ) {
        this(
                sessionManager,
                connectionRegistry,
                Clock.systemUTC(),
                Duration.ofMillis(GameConstants.INVITATION_TTL_MILLIS),
                DiceRoller.random(),
                Objects.requireNonNull(matchService, "matchService")
        );
    }

    public RoomService(
            SessionManager sessionManager,
            ConnectionRegistry connectionRegistry,
            DiceRoller diceRoller
    ) {
        this(
                sessionManager,
                connectionRegistry,
                Clock.systemUTC(),
                Duration.ofMillis(GameConstants.INVITATION_TTL_MILLIS),
                diceRoller,
                null
        );
    }

    RoomService(
            SessionManager sessionManager,
            ConnectionRegistry connectionRegistry,
            Clock clock,
            Duration invitationTtl
    ) {
        this(sessionManager, connectionRegistry, clock, invitationTtl, DiceRoller.random(), null);
    }

    RoomService(
            SessionManager sessionManager,
            ConnectionRegistry connectionRegistry,
            Clock clock,
            Duration invitationTtl,
            DiceRoller diceRoller
    ) {
        this(sessionManager, connectionRegistry, clock, invitationTtl, diceRoller, null);
    }

    RoomService(
            SessionManager sessionManager,
            ConnectionRegistry connectionRegistry,
            Clock clock,
            Duration invitationTtl,
            DiceRoller diceRoller,
            MatchService matchService
    ) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.connectionRegistry = Objects.requireNonNull(connectionRegistry, "connectionRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.invitationTtl = Objects.requireNonNull(invitationTtl, "invitationTtl");
        this.diceRoller = Objects.requireNonNull(diceRoller, "diceRoller");
        this.matchService = matchService;
        if (invitationTtl.isZero() || invitationTtl.isNegative()) {
            throw new IllegalArgumentException("invitationTtl must be positive");
        }
        this.messageFactory = new MessageFactory(new JsonMessageCodec().objectMapper());
        this.timeoutManager = new TimeoutManager(clock);
        this.previousPresence = presenceSnapshot();
    }

    public RoomDto createRoom(String sessionId, String connectionId) {
        PlayerSession session = sessionManager.requireAuthenticated(sessionId, connectionId);
        requireNotInRoom(session.user().id());
        requireIdle(session);
        GameRoom room = roomManager.create(session);
        try {
            sessionManager.updatePresence(sessionId, PlayerPresenceState.IN_ROOM);
            invalidateInvitationForTarget(session.user().id());
            RoomDto snapshot = snapshot(room);
            LOGGER.info("Player {} created room {}", session.user().id(), room.roomId());
            return snapshot;
        } catch (RuntimeException exception) {
            roomManager.leaveCurrent(session.user().id());
            throw exception;
        }
    }

    public RoomDto joinRoom(String sessionId, String connectionId, String roomId) {
        PlayerSession session = sessionManager.requireAuthenticated(sessionId, connectionId);
        requireNotInRoom(session.user().id());
        requireIdle(session);
        requireRoomId(roomId);
        GameRoom room = roomManager.join(roomId, session);
        try {
            sessionManager.updatePresence(sessionId, PlayerPresenceState.IN_ROOM);
            invalidateInvitationsForRoom(room.roomId());
            RoomDto snapshot = snapshot(room);
            LOGGER.info("Player {} joined room {}", session.user().id(), room.roomId());
            return snapshot;
        } catch (RuntimeException exception) {
            roomManager.leaveCurrent(session.user().id());
            throw exception;
        }
    }

    public void leaveRoom(String sessionId, String connectionId, String roomId) {
        PlayerSession session = sessionManager.requireAuthenticated(sessionId, connectionId);
        requireRoomId(roomId);
        long userId = session.user().id();
        GameRoom room = roomManager.requireRoomForPlayer(roomId, userId);
        Optional<GameStateDto> forfeited = room.forfeitActivePlayer(
                userId,
                clock.instant(),
                presenceSnapshot()
        );
        GameRoom changedRoom = roomManager.leaveCurrent(userId)
                .or(() -> roomManager.departCurrent(userId))
                .orElseThrow(() -> new RoomException(
                        ErrorCode.NOT_IN_ROOM,
                        "Player cannot leave this room"
                ));
        sessionManager.updatePresence(sessionId, PlayerPresenceState.IDLE);
        invalidateInvitationsForRoom(roomId);
        if (forfeited.isPresent()) {
            // Quit chủ động trong trận bỏ qua grace period và dùng chung luật forfeit authoritative.
            persistCompletedMatch(changedRoom);
            scheduleTurnTimeout(changedRoom);
            LOGGER.info("Player {} quit and forfeited room {}", userId, roomId);
        } else {
            LOGGER.info("Player {} left room {}", userId, roomId);
        }
    }

    public InvitationDto invitePlayer(
            String sessionId,
            String connectionId,
            String roomId,
            String targetPlayerId
    ) {
        PlayerSession inviter = sessionManager.requireAuthenticated(sessionId, connectionId);
        requireRoomId(roomId);
        long targetUserId = parsePlayerId(targetPlayerId);
        if (targetUserId == inviter.user().id()) {
            throw new RoomException(ErrorCode.INVALID_REQUEST, "A player cannot invite themselves");
        }
        GameRoom room = roomManager.requireRoomForPlayer(roomId, inviter.user().id());
        room.validateInvitation(inviter.user().id());
        PlayerSession target = sessionManager.findByUserId(targetUserId)
                .filter(PlayerSession::connected)
                .filter(session -> session.presenceState() == PlayerPresenceState.IDLE)
                .orElseThrow(() -> new RoomException(
                        ErrorCode.PLAYER_NOT_IDLE,
                        "Invited player must be online and idle"
                ));

        Instant expiresAt = clock.instant().plus(invitationTtl);
        PendingInvitation pending = new PendingInvitation(
                UUID.randomUUID().toString(),
                roomId,
                inviter.user().id(),
                inviter.user().displayName(),
                target.user().id(),
                expiresAt
        );
        synchronized (invitationLock) {
            invalidateInvitationForTargetLocked(targetUserId);
            invitationsById.put(pending.invitationId(), pending);
            invitationIdByTargetUserId.put(targetUserId, pending.invitationId());
        }
        try {
            room.validateInvitation(inviter.user().id());
        } catch (RuntimeException exception) {
            invalidateInvitationForTarget(targetUserId);
            throw exception;
        }
        LOGGER.info("Player {} invited player {} to room {}", inviter.user().id(), targetUserId, roomId);
        return pending.toDto();
    }

    public void deliverInvitation(InvitationDto invitation) {
        PendingInvitation pending;
        synchronized (invitationLock) {
            pending = invitationsById.get(invitation.invitationId());
        }
        if (pending == null) {
            throw new RoomException(ErrorCode.INVITATION_NOT_FOUND, "Invitation no longer exists");
        }
        PlayerSession target = sessionManager.findByUserId(pending.targetUserId())
                .filter(PlayerSession::connected)
                .filter(session -> session.presenceState() == PlayerPresenceState.IDLE)
                .orElseThrow(() -> new RoomException(
                        ErrorCode.PLAYER_NOT_IDLE,
                        "Invited player must still be online and idle"
                ));
        ClientConnection connection = connectionRegistry.find(target.connectionId())
                .orElseThrow(() -> new RoomException(ErrorCode.PLAYER_NOT_IDLE, "Invited player is offline"));
        try {
            connection.send(messageFactory.event(MessageType.INVITE_PLAYER, invitation));
        } catch (IOException exception) {
            invalidateInvitationForTarget(pending.targetUserId());
            connection.close();
            throw new RoomException(ErrorCode.PLAYER_NOT_IDLE, "Unable to deliver invitation");
        }
    }

    public RoomDto acceptInvitation(String sessionId, String connectionId, String invitationId) {
        PlayerSession target = sessionManager.requireAuthenticated(sessionId, connectionId);
        PendingInvitation invitation = consumeInvitation(invitationId, target.user().id());
        return joinRoom(sessionId, connectionId, invitation.roomId());
    }

    public void rejectInvitation(String sessionId, String connectionId, String invitationId) {
        PlayerSession target = sessionManager.requireAuthenticated(sessionId, connectionId);
        consumeInvitation(invitationId, target.user().id());
        LOGGER.info("Player {} rejected invitation {}", target.user().id(), invitationId);
    }

    public RoomDto setReady(
            String sessionId,
            String connectionId,
            String roomId,
            boolean ready
    ) {
        PlayerSession session = sessionManager.requireAuthenticated(sessionId, connectionId);
        requireRoomId(roomId);
        GameRoom room = roomManager.requireRoomForPlayer(roomId, session.user().id());
        room.setReady(session.user().id(), ready);
        LOGGER.info("Player {} set ready={} in room {}", session.user().id(), ready, roomId);
        return snapshot(room);
    }

    public GameStateDto startGame(String sessionId, String connectionId, String roomId) {
        PlayerSession host = sessionManager.requireAuthenticated(sessionId, connectionId);
        requireRoomId(roomId);
        GameRoom room = roomManager.requireRoomForPlayer(roomId, host.user().id());
        GameStateDto gameState = room.start(host.user().id(), presenceSnapshot(), clock.instant());
        for (Long userId : room.memberUserIds()) {
            sessionManager.updatePresenceForUser(userId, PlayerPresenceState.PLAYING);
        }
        invalidateInvitationsForRoom(roomId);
        LOGGER.info("Player {} started game {} in room {}", host.user().id(), gameState.matchId(), roomId);
        scheduleTurnTimeout(room);
        return room.gameSnapshot(presenceSnapshot());
    }

    public DiceResultDto rollDice(String sessionId, String connectionId, String roomId) {
        PlayerSession player = sessionManager.requireAuthenticated(sessionId, connectionId);
        requireRoomId(roomId);
        GameRoom room = roomManager.requireRoomForPlayer(roomId, player.user().id());
        Instant now = clock.instant();
        if (expireBeforeRequest(room, now)) {
            throw new RoomException(ErrorCode.TURN_TIMEOUT, "The roll phase has already timed out");
        }
        DiceResultDto result = room.rollDice(player.user().id(), diceRoller, now);
        scheduleTurnTimeout(room);
        LOGGER.info(
                "Player {} rolled {} in room {}; valid pieces={}",
                player.user().id(),
                result.diceValue(),
                roomId,
                result.validPieceIds().size()
        );
        return result;
    }

    public MovePieceResultDto movePiece(
            String sessionId,
            String connectionId,
            String roomId,
            String pieceId
    ) {
        PlayerSession player = sessionManager.requireAuthenticated(sessionId, connectionId);
        requireRoomId(roomId);
        if (pieceId == null || pieceId.isBlank()) {
            throw new RoomException(ErrorCode.INVALID_REQUEST, "Piece ID is required");
        }
        GameRoom room = roomManager.requireRoomForPlayer(roomId, player.user().id());
        Instant now = clock.instant();
        if (expireBeforeRequest(room, now)) {
            throw new RoomException(ErrorCode.TURN_TIMEOUT, "The move phase has already timed out");
        }
        MovePieceResultDto result = room.movePiece(
                player.user().id(),
                pieceId,
                now,
                presenceSnapshot()
        );
        persistCompletedMatch(room);
        scheduleTurnTimeout(room);
        LOGGER.info(
                "Player {} moved piece {} in room {}; captured={}, bonus={}",
                player.user().id(),
                pieceId,
                roomId,
                result.capturedPieceId(),
                result.bonusRoll()
        );
        return result;
    }

    public void leaveForSessionEnd(long userId) {
        roomManager.findByPlayer(userId).ifPresent(room -> {
            Map<Long, PlayerPresenceState> presence = presenceSnapshot();
            Optional<GameStateDto> forfeited = room.forfeitActivePlayer(userId, clock.instant(), presence);
            Optional<GameRoom> departed = roomManager.leaveCurrent(userId)
                    .or(() -> roomManager.departCurrent(userId));
            if (forfeited.isPresent()) {
                // Logout/quit trong trận không có grace period: xử lý forfeit ngay lập tức.
                persistCompletedMatch(room);
                scheduleTurnTimeout(room);
                LOGGER.info("Player {} forfeited room {} after session ended", userId, room.roomId());
            }
            departed.ifPresent(changedRoom -> {
                LOGGER.info(
                        "Removed player {} from room {} after session ended",
                        userId,
                        changedRoom.roomId()
                );
                invalidateInvitationsForRoom(changedRoom.roomId());
                if (!changedRoom.isClosed()) {
                    broadcast(changedRoom);
                    // Participant COMPLETED cũng có thể logout/rời sớm; luôn đồng bộ lại presence.
                    broadcastGameUpdated(changedRoom.roomId());
                }
            });
        });
    }

    public Optional<RoomDto> roomForPlayer(long userId) {
        return roomManager.findByPlayer(userId).map(this::snapshot);
    }

    public Optional<GameStateDto> gameForPlayer(long userId) {
        return roomManager.findByPlayer(userId)
                .flatMap(room -> room.gameSnapshotIfStarted(presenceSnapshot()));
    }

    public void broadcastRoom(String roomId) {
        roomManager.findById(roomId).ifPresent(this::broadcast);
    }

    public void broadcastGame(String roomId) {
        roomManager.findById(roomId).ifPresent(room -> room
                .gameSnapshotIfStarted(presenceSnapshot())
                .ifPresent(gameState -> broadcast(room, MessageType.GAME_STATE, gameState))
        );
    }

    public void broadcastGameUpdated(String roomId) {
        roomManager.findById(roomId).ifPresent(room -> room
                .gameSnapshotIfStarted(presenceSnapshot())
                .ifPresent(gameState -> {
                    broadcast(room, MessageType.GAME_STATE_UPDATED, gameState);
                    room.gameOverSnapshot().ifPresent(gameOver ->
                            broadcast(room, MessageType.GAME_OVER, gameOver)
                    );
                })
        );
    }

    public void broadcastDiceResult(String roomId, DiceResultDto result) {
        roomManager.findById(roomId)
                .ifPresent(room -> broadcast(room, MessageType.DICE_RESULT, result));
    }

    void processExpiredTurns() {
        for (GameRoom room : roomManager.snapshot()) {
            room.timeoutExpectation().ifPresent(expectation -> handleScheduledTimeout(room, expectation));
        }
    }

    /**
     * Đồng bộ thay đổi session vào phòng. DISCONNECTED vẫn còn trong snapshot nên chỉ
     * cập nhật presence; chỉ khi session biến mất sau grace period mới xử lý remove ở
     * phòng chờ hoặc FORFEITED trong trận đang chạy.
     */
    public synchronized void onSessionsChanged() {
        if (closed.get()) {
            return;
        }
        Map<Long, PlayerPresenceState> currentPresence = presenceSnapshot();
        Set<String> affectedRoomIds = new HashSet<>();
        Set<String> forfeitedRoomIds = new HashSet<>();

        for (GameRoom room : roomManager.snapshot()) {
            List<Long> expiredUserIds = room.memberUserIds().stream()
                    .filter(memberUserId -> !currentPresence.containsKey(memberUserId))
                    .toList();
            if (expiredUserIds.isEmpty()) {
                continue;
            }

            Optional<GameStateDto> forfeited = room.forfeitActivePlayers(
                    expiredUserIds,
                    clock.instant(),
                    currentPresence
            );
            if (forfeited.isPresent()) {
                expiredUserIds.forEach(roomManager::departCurrent);
                persistCompletedMatch(room);
                affectedRoomIds.add(room.roomId());
                forfeitedRoomIds.add(room.roomId());
                scheduleTurnTimeout(room);
                expiredUserIds.forEach(memberUserId ->
                        LOGGER.info(
                                "Player {} forfeited room {} after reconnect grace period",
                                memberUserId,
                                room.roomId()
                        )
                );
            } else {
                expiredUserIds.forEach(memberUserId -> roomManager.leaveCurrent(memberUserId)
                        .or(() -> roomManager.departCurrent(memberUserId))
                        .filter(changedRoom -> !changedRoom.isClosed())
                        .ifPresent(changedRoom -> {
                            affectedRoomIds.add(changedRoom.roomId());
                            invalidateInvitationsForRoom(changedRoom.roomId());
                            LOGGER.info(
                                    "Removed player {} from room {} after reconnect grace period",
                                    memberUserId,
                                    room.roomId()
                            );
                        }));
            }
        }
        Set<Long> candidates = new HashSet<>(currentPresence.keySet());
        candidates.addAll(previousPresence.keySet());
        for (Long userId : candidates) {
            if (!Objects.equals(previousPresence.get(userId), currentPresence.get(userId))) {
                roomManager.findByPlayer(userId)
                        .ifPresent(room -> affectedRoomIds.add(room.roomId()));
            }
        }
        previousPresence = currentPresence;
        affectedRoomIds.forEach(roomId -> {
            broadcastRoom(roomId);
            if (forfeitedRoomIds.contains(roomId)) {
                broadcastGameUpdated(roomId);
            } else {
                broadcastGame(roomId);
            }
        });
    }

    private RoomDto snapshot(GameRoom room) {
        return room.snapshot(presenceSnapshot());
    }

    private boolean expireBeforeRequest(GameRoom room, Instant now) {
        Optional<GameRoom.TimeoutOutcome> outcome = room.expireCurrentPhase(now, presenceSnapshot());
        outcome.ifPresent(value -> handleTimeoutOutcome(room, value));
        return outcome.isPresent();
    }

    private void scheduleTurnTimeout(GameRoom room) {
        Optional<GameRoom.TimeoutExpectation> expectation = room.timeoutExpectation();
        if (expectation.isEmpty()) {
            timeoutManager.cancel(room.roomId());
            return;
        }
        GameRoom.TimeoutExpectation expected = expectation.get();
        timeoutManager.schedule(
                room.roomId(),
                expected.deadlineEpochMillis(),
                () -> handleScheduledTimeout(room, expected)
        );
    }

    private void handleScheduledTimeout(GameRoom room, GameRoom.TimeoutExpectation expectation) {
        Optional<GameRoom.TimeoutOutcome> outcome = room.expireIfExpected(
                expectation,
                clock.instant(),
                presenceSnapshot()
        );
        if (outcome.isPresent()) {
            handleTimeoutOutcome(room, outcome.get());
        } else {
            scheduleTurnTimeout(room);
        }
    }

    private void handleTimeoutOutcome(GameRoom room, GameRoom.TimeoutOutcome outcome) {
        scheduleTurnTimeout(room);
        TurnTimeoutDto timeout = outcome.timeout();
        LOGGER.info(
                "Player {} timed out in {} for room {}",
                timeout.playerId(),
                timeout.timedOutState(),
                room.roomId()
        );
        broadcast(room, MessageType.TURN_TIMEOUT, timeout);
        broadcast(room, MessageType.GAME_STATE_UPDATED, outcome.gameState());
    }

    private Map<Long, PlayerPresenceState> presenceSnapshot() {
        Map<Long, PlayerPresenceState> presence = new HashMap<>();
        for (SessionSnapshot snapshot : sessionManager.snapshots()) {
            presence.put(snapshot.userId(), snapshot.presenceState());
        }
        return Map.copyOf(presence);
    }

    private void persistCompletedMatch(GameRoom room) {
        if (matchService == null) {
            return;
        }
        synchronized (room) {
            if (room.matchPersisted()) {
                return;
            }
            room.completedMatchSnapshot().ifPresent(match -> {
                var gameOver = matchService.completeMatch(room.roomId(), match);
                room.markMatchPersisted(gameOver);
                LOGGER.info("Persisted completed match {} for room {}", match.matchId(), room.roomId());
            });
        }
    }

    private void broadcast(GameRoom room) {
        if (room.isClosed()) {
            return;
        }
        RoomDto snapshot = snapshot(room);
        broadcast(room, MessageType.ROOM_UPDATED, new RoomPayload(snapshot));
    }

    private void broadcast(GameRoom room, MessageType type, Object payload) {
        MessageEnvelope event = messageFactory.event(type, payload);
        for (Long userId : room.memberUserIds()) {
            sessionManager.findByUserId(userId)
                    .filter(PlayerSession::connected)
                    .flatMap(session -> connectionRegistry.find(session.connectionId()))
                    .ifPresent(connection -> send(connection, event));
        }
    }

    private static void requireIdle(PlayerSession session) {
        if (session.presenceState() != PlayerPresenceState.IDLE) {
            throw new RoomException(ErrorCode.PLAYER_NOT_IDLE, "Player must be idle to enter a room");
        }
    }

    private void requireNotInRoom(long userId) {
        if (roomManager.findByPlayer(userId).isPresent()) {
            throw new RoomException(ErrorCode.ALREADY_IN_ROOM, "Player is already in a room");
        }
    }

    private PendingInvitation consumeInvitation(String invitationId, long targetUserId) {
        if (invitationId == null || invitationId.isBlank()) {
            throw new RoomException(ErrorCode.INVALID_REQUEST, "Invitation ID is required");
        }
        PendingInvitation invitation;
        synchronized (invitationLock) {
            invitation = invitationsById.get(invitationId);
            if (invitation == null || invitation.targetUserId() != targetUserId) {
                throw new RoomException(ErrorCode.INVITATION_NOT_FOUND, "Invitation does not exist");
            }
            invitationsById.remove(invitationId);
            invitationIdByTargetUserId.remove(targetUserId, invitationId);
        }
        if (!clock.instant().isBefore(invitation.expiresAt())) {
            throw new RoomException(ErrorCode.INVITATION_EXPIRED, "Invitation has expired");
        }
        return invitation;
    }

    private void invalidateInvitationForTarget(long targetUserId) {
        synchronized (invitationLock) {
            invalidateInvitationForTargetLocked(targetUserId);
        }
    }

    private void invalidateInvitationForTargetLocked(long targetUserId) {
        String invitationId = invitationIdByTargetUserId.remove(targetUserId);
        if (invitationId != null) {
            invitationsById.remove(invitationId);
        }
    }

    private void invalidateInvitationsForRoom(String roomId) {
        synchronized (invitationLock) {
            invitationsById.values().removeIf(invitation -> {
                if (!invitation.roomId().equals(roomId)) {
                    return false;
                }
                invitationIdByTargetUserId.remove(invitation.targetUserId(), invitation.invitationId());
                return true;
            });
        }
    }

    private static long parsePlayerId(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            throw new RoomException(ErrorCode.INVALID_REQUEST, "Player ID is required");
        }
        try {
            return Long.parseLong(playerId);
        } catch (NumberFormatException exception) {
            throw new RoomException(ErrorCode.INVALID_REQUEST, "Player ID is invalid");
        }
    }

    private static void requireRoomId(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            throw new RoomException(ErrorCode.INVALID_REQUEST, "Room ID is required");
        }
    }

    private static void send(ClientConnection connection, MessageEnvelope event) {
        try {
            connection.send(event);
        } catch (IOException exception) {
            LOGGER.debug(
                    "Closing connection {} after room broadcast failed: {}",
                    connection.id(),
                    exception.getMessage()
            );
            connection.close();
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            timeoutManager.close();
        }
    }

    private record PendingInvitation(
            String invitationId,
            String roomId,
            long inviterUserId,
            String inviterDisplayName,
            long targetUserId,
            Instant expiresAt
    ) {
        InvitationDto toDto() {
            return new InvitationDto(
                    invitationId,
                    roomId,
                    Long.toString(inviterUserId),
                    inviterDisplayName,
                    expiresAt.toEpochMilli()
            );
        }
    }
}
