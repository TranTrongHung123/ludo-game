package vn.ptit.ltm.server.room;

import vn.ptit.ltm.common.dto.room.RoomDto;
import vn.ptit.ltm.common.dto.room.RoomPlayerDto;
import vn.ptit.ltm.common.dto.game.DiceResultDto;
import vn.ptit.ltm.common.dto.game.GameStateDto;
import vn.ptit.ltm.common.dto.game.MatchParticipantDto;
import vn.ptit.ltm.common.dto.game.MovePieceResultDto;
import vn.ptit.ltm.common.dto.game.PieceDto;
import vn.ptit.ltm.common.dto.game.TurnTimeoutDto;
import vn.ptit.ltm.common.enums.MatchParticipantStatus;
import vn.ptit.ltm.common.enums.PieceColor;
import vn.ptit.ltm.common.enums.PieceState;
import vn.ptit.ltm.common.enums.PlayerPresenceState;
import vn.ptit.ltm.common.enums.RoomState;
import vn.ptit.ltm.common.enums.TurnState;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.common.model.BoardConstants;
import vn.ptit.ltm.common.model.GameConstants;
import vn.ptit.ltm.server.game.DiceRoller;
import vn.ptit.ltm.server.game.GameEngine;
import vn.ptit.ltm.server.game.SpecialCellLayout;
import vn.ptit.ltm.server.session.PlayerSession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.UUID;

final class GameRoom {
    private final String roomId;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<Integer, RoomMember> membersBySlot = new HashMap<>();
    private RoomState state = RoomState.WAITING;
    private long nextJoinOrder;
    private long hostUserId;
    private boolean closed;
    private GameStateDto gameState;

    GameRoom(String roomId, PlayerSession creator) {
        this.roomId = roomId;
        RoomMember host = member(creator, 0, nextJoinOrder++);
        membersBySlot.put(0, host);
        hostUserId = host.userId();
    }

    String roomId() {
        return roomId;
    }

    void add(PlayerSession session) {
        lock.lock();
        try {
            if (closed) {
                throw new RoomException(ErrorCode.ROOM_NOT_FOUND, "Room does not exist");
            }
            if (state != RoomState.WAITING) {
                throw new RoomException(ErrorCode.GAME_ALREADY_STARTED, "Game has already started");
            }
            if (membersBySlot.values().stream().anyMatch(member -> member.userId() == session.user().id())) {
                throw new RoomException(ErrorCode.ALREADY_IN_ROOM, "Player is already in this room");
            }
            int slot = firstFreeSlot();
            if (slot < 0) {
                throw new RoomException(ErrorCode.ROOM_FULL, "Room is full");
            }
            membersBySlot.put(slot, member(session, slot, nextJoinOrder++));
        } finally {
            lock.unlock();
        }
    }

    boolean removeIfWaiting(long userId) {
        lock.lock();
        try {
            if (state != RoomState.WAITING) {
                return false;
            }
            RoomMember removed = membersBySlot.values().stream()
                    .filter(member -> member.userId() == userId)
                    .findFirst()
                    .orElse(null);
            if (removed == null) {
                return false;
            }
            membersBySlot.remove(removed.slotIndex());
            if (membersBySlot.isEmpty()) {
                closed = true;
                return true;
            }
            if (hostUserId == userId) {
                hostUserId = membersBySlot.values().stream()
                        .min(Comparator.comparingLong(RoomMember::joinOrder))
                        .orElseThrow()
                        .userId();
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    void validateInvitation(long requesterUserId) {
        lock.lock();
        try {
            requireOpenWaiting();
            if (hostUserId != requesterUserId) {
                throw new RoomException(ErrorCode.NOT_ROOM_HOST, "Only the room host can invite players");
            }
            if (membersBySlot.size() >= BoardConstants.MAX_PLAYERS) {
                throw new RoomException(ErrorCode.ROOM_FULL, "Room is full");
            }
        } finally {
            lock.unlock();
        }
    }

    void setReady(long userId, boolean ready) {
        lock.lock();
        try {
            requireOpenWaiting();
            RoomMember member = membersBySlot.values().stream()
                    .filter(candidate -> candidate.userId() == userId)
                    .findFirst()
                    .orElseThrow(() -> new RoomException(ErrorCode.NOT_IN_ROOM, "Player is not in this room"));
            membersBySlot.put(member.slotIndex(), member.withReady(ready));
        } finally {
            lock.unlock();
        }
    }

    GameStateDto start(long requesterUserId, Map<Long, PlayerPresenceState> presenceByUserId, Instant now) {
        lock.lock();
        try {
            requireOpenWaiting();
            if (hostUserId != requesterUserId) {
                throw new RoomException(ErrorCode.NOT_ROOM_HOST, "Only the room host can start the game");
            }
            if (membersBySlot.size() < BoardConstants.MIN_PLAYERS) {
                throw new RoomException(ErrorCode.NOT_ENOUGH_PLAYERS, "At least 2 players are required");
            }
            if (membersBySlot.values().stream().anyMatch(member -> !member.ready())) {
                throw new RoomException(ErrorCode.PLAYER_NOT_READY, "Every player must be ready");
            }
            if (membersBySlot.values().stream().anyMatch(member ->
                    presenceByUserId.get(member.userId()) != PlayerPresenceState.IN_ROOM)) {
                throw new RoomException(ErrorCode.PLAYER_NOT_READY, "Every player must be connected and in the room");
            }

            List<RoomMember> orderedMembers = membersBySlot.values().stream()
                    .sorted(Comparator.comparingInt(RoomMember::slotIndex))
                    .toList();
            RoomMember first = orderedMembers.getFirst();
            state = RoomState.PLAYING;
            gameState = new GameStateDto(
                    roomId,
                    UUID.randomUUID().toString(),
                    RoomState.PLAYING,
                    Long.toString(first.userId()),
                    first.slotIndex(),
                    TurnState.WAITING_FOR_ROLL,
                    null,
                    List.of(),
                    GameConstants.ROLL_PHASE_DURATION_MILLIS,
                    now.toEpochMilli() + GameConstants.ROLL_PHASE_DURATION_MILLIS,
                    orderedMembers.stream().map(GameRoom::participant).toList(),
                    SpecialCellLayout.canonical(),
                    0L
            );
            return gameState;
        } finally {
            lock.unlock();
        }
    }

    DiceResultDto rollDice(long userId, DiceRoller diceRoller, Instant now) {
        lock.lock();
        try {
            if (gameState == null) {
                throw new RoomException(ErrorCode.GAME_NOT_STARTED, "Game has not started");
            }
            GameEngine.RollOutcome outcome = GameEngine.rollDice(
                    gameState,
                    Long.toString(userId),
                    diceRoller,
                    now
            );
            gameState = outcome.gameState();
            return outcome.diceResult();
        } finally {
            lock.unlock();
        }
    }

    MovePieceResultDto movePiece(
            long userId,
            String pieceId,
            Instant now,
            Map<Long, PlayerPresenceState> presenceByUserId
    ) {
        lock.lock();
        try {
            if (gameState == null) {
                throw new RoomException(ErrorCode.GAME_NOT_STARTED, "Game has not started");
            }
            GameEngine.MoveOutcome outcome = GameEngine.movePiece(
                    gameState,
                    Long.toString(userId),
                    pieceId,
                    now
            );
            gameState = outcome.gameState();
            state = gameState.roomState();
            MovePieceResultDto result = outcome.result();
            return new MovePieceResultDto(
                    result.piece(),
                    result.capturedPieceId(),
                    result.triggeredEffect(),
                    result.shieldConsumed(),
                    result.bonusRoll(),
                    gameSnapshotLocked(presenceByUserId)
            );
        } finally {
            lock.unlock();
        }
    }

    Optional<TimeoutExpectation> timeoutExpectation() {
        lock.lock();
        try {
            if (gameState == null
                    || gameState.roomState() != RoomState.PLAYING
                    || (gameState.turnState() != TurnState.WAITING_FOR_ROLL
                    && gameState.turnState() != TurnState.WAITING_FOR_MOVE)
                    || gameState.serverDeadlineEpochMillis() == null) {
                return Optional.empty();
            }
            return Optional.of(new TimeoutExpectation(
                    roomId,
                    gameState.matchId(),
                    gameState.stateVersion(),
                    gameState.currentPlayerId(),
                    gameState.turnState(),
                    gameState.serverDeadlineEpochMillis()
            ));
        } finally {
            lock.unlock();
        }
    }

    Optional<TimeoutOutcome> expireCurrentPhase(
            Instant now,
            Map<Long, PlayerPresenceState> presenceByUserId
    ) {
        lock.lock();
        try {
            if (gameState == null) {
                return Optional.empty();
            }
            TimeoutExpectation expectation = timeoutExpectationLocked();
            if (expectation == null) {
                return Optional.empty();
            }
            return expireLocked(expectation, now, presenceByUserId);
        } finally {
            lock.unlock();
        }
    }

    Optional<TimeoutOutcome> expireIfExpected(
            TimeoutExpectation expectation,
            Instant now,
            Map<Long, PlayerPresenceState> presenceByUserId
    ) {
        lock.lock();
        try {
            return expireLocked(expectation, now, presenceByUserId);
        } finally {
            lock.unlock();
        }
    }

    GameStateDto gameSnapshot(Map<Long, PlayerPresenceState> presenceByUserId) {
        lock.lock();
        try {
            return gameSnapshotLocked(presenceByUserId);
        } finally {
            lock.unlock();
        }
    }

    Optional<GameStateDto> gameSnapshotIfStarted(Map<Long, PlayerPresenceState> presenceByUserId) {
        lock.lock();
        try {
            if (gameState == null) {
                return Optional.empty();
            }
        } finally {
            lock.unlock();
        }
        return Optional.of(gameSnapshot(presenceByUserId));
    }

    boolean isClosed() {
        lock.lock();
        try {
            return closed;
        } finally {
            lock.unlock();
        }
    }

    boolean hasPlayer(long userId) {
        lock.lock();
        try {
            return membersBySlot.values().stream().anyMatch(member -> member.userId() == userId);
        } finally {
            lock.unlock();
        }
    }

    List<Long> memberUserIds() {
        lock.lock();
        try {
            return membersBySlot.values().stream()
                    .sorted(Comparator.comparingInt(RoomMember::slotIndex))
                    .map(RoomMember::userId)
                    .toList();
        } finally {
            lock.unlock();
        }
    }

    RoomDto snapshot(Map<Long, PlayerPresenceState> presenceByUserId) {
        lock.lock();
        try {
            if (closed || membersBySlot.isEmpty()) {
                throw new RoomException(ErrorCode.ROOM_NOT_FOUND, "Room does not exist");
            }
            List<RoomPlayerDto> players = membersBySlot.values().stream()
                    .sorted(Comparator.comparingInt(RoomMember::slotIndex))
                    .map(member -> new RoomPlayerDto(
                            Long.toString(member.userId()),
                            member.displayName(),
                            member.slotIndex(),
                            PieceColor.fromSlotIndex(member.slotIndex()),
                            member.ready(),
                            presenceByUserId.getOrDefault(
                                    member.userId(),
                                    PlayerPresenceState.DISCONNECTED
                            )
                    ))
                    .toList();
            return new RoomDto(
                    roomId,
                    Long.toString(hostUserId),
                    state,
                    players
            );
        } finally {
            lock.unlock();
        }
    }

    private int firstFreeSlot() {
        for (int slot = 0; slot < BoardConstants.MAX_PLAYERS; slot++) {
            if (!membersBySlot.containsKey(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private GameStateDto gameSnapshotLocked(Map<Long, PlayerPresenceState> presenceByUserId) {
        if (gameState == null) {
            throw new RoomException(ErrorCode.GAME_NOT_STARTED, "Game has not started");
        }
        List<MatchParticipantDto> participants = gameState.participants().stream()
                .map(participant -> new MatchParticipantDto(
                        participant.playerId(),
                        participant.displayName(),
                        participant.slotIndex(),
                        participant.color(),
                        presenceByUserId.getOrDefault(
                                Long.parseLong(participant.playerId()),
                                PlayerPresenceState.DISCONNECTED
                        ),
                        participant.matchStatus(),
                        participant.rank(),
                        participant.scoreEarned(),
                        participant.pieces()
                ))
                .toList();
        return new GameStateDto(
                gameState.roomId(),
                gameState.matchId(),
                gameState.roomState(),
                gameState.currentPlayerId(),
                gameState.currentSlot(),
                gameState.turnState(),
                gameState.diceValue(),
                gameState.validPieceIds(),
                gameState.phaseDurationMillis(),
                gameState.serverDeadlineEpochMillis(),
                participants,
                gameState.specialCells(),
                gameState.stateVersion()
        );
    }

    private TimeoutExpectation timeoutExpectationLocked() {
        if (gameState == null
                || gameState.roomState() != RoomState.PLAYING
                || (gameState.turnState() != TurnState.WAITING_FOR_ROLL
                && gameState.turnState() != TurnState.WAITING_FOR_MOVE)
                || gameState.serverDeadlineEpochMillis() == null) {
            return null;
        }
        return new TimeoutExpectation(
                roomId,
                gameState.matchId(),
                gameState.stateVersion(),
                gameState.currentPlayerId(),
                gameState.turnState(),
                gameState.serverDeadlineEpochMillis()
        );
    }

    private Optional<TimeoutOutcome> expireLocked(
            TimeoutExpectation expectation,
            Instant now,
            Map<Long, PlayerPresenceState> presenceByUserId
    ) {
        if (gameState == null
                || !roomId.equals(expectation.roomId())
                || !gameState.matchId().equals(expectation.matchId())
                || gameState.stateVersion() != expectation.stateVersion()
                || !Objects.equals(gameState.currentPlayerId(), expectation.playerId())
                || gameState.turnState() != expectation.turnState()
                || gameState.serverDeadlineEpochMillis() == null
                || now.toEpochMilli() < gameState.serverDeadlineEpochMillis()) {
            return Optional.empty();
        }
        TurnTimeoutDto timeout = new TurnTimeoutDto(
                roomId,
                gameState.currentPlayerId(),
                gameState.turnState()
        );
        gameState = GameEngine.timeoutTurn(gameState, now);
        state = gameState.roomState();
        return Optional.of(new TimeoutOutcome(timeout, gameSnapshotLocked(presenceByUserId)));
    }

    private void requireOpenWaiting() {
        if (closed) {
            throw new RoomException(ErrorCode.ROOM_NOT_FOUND, "Room does not exist");
        }
        if (state != RoomState.WAITING) {
            throw new RoomException(ErrorCode.GAME_ALREADY_STARTED, "Game has already started");
        }
    }

    private static MatchParticipantDto participant(RoomMember member) {
        String playerId = Long.toString(member.userId());
        PieceColor color = PieceColor.fromSlotIndex(member.slotIndex());
        List<PieceDto> pieces = new ArrayList<>(BoardConstants.PIECES_PER_PLAYER);
        for (int index = 1; index <= BoardConstants.PIECES_PER_PLAYER; index++) {
            pieces.add(new PieceDto(
                    playerId + "-piece-" + index,
                    playerId,
                    color,
                    PieceState.IN_YARD,
                    BoardConstants.YARD_STEP,
                    false,
                    false
            ));
        }
        return new MatchParticipantDto(
                playerId,
                member.displayName(),
                member.slotIndex(),
                color,
                PlayerPresenceState.PLAYING,
                MatchParticipantStatus.ACTIVE,
                null,
                BigDecimal.ZERO,
                pieces
        );
    }

    private static RoomMember member(PlayerSession session, int slot, long joinOrder) {
        return new RoomMember(
                session.user().id(),
                session.user().displayName(),
                slot,
                joinOrder,
                false
        );
    }

    private record RoomMember(
            long userId,
            String displayName,
            int slotIndex,
            long joinOrder,
            boolean ready
    ) {
        RoomMember withReady(boolean newReady) {
            return new RoomMember(userId, displayName, slotIndex, joinOrder, newReady);
        }
    }

    record TimeoutExpectation(
            String roomId,
            String matchId,
            long stateVersion,
            String playerId,
            TurnState turnState,
            long deadlineEpochMillis
    ) {
    }

    record TimeoutOutcome(TurnTimeoutDto timeout, GameStateDto gameState) {
    }
}
