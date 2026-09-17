package vn.ptit.ltm.server.game;

import vn.ptit.ltm.common.dto.game.DiceResultDto;
import vn.ptit.ltm.common.dto.game.GameStateDto;
import vn.ptit.ltm.common.dto.game.MatchParticipantDto;
import vn.ptit.ltm.common.dto.game.MovePieceResultDto;
import vn.ptit.ltm.common.dto.game.PieceDto;
import vn.ptit.ltm.common.dto.game.SpecialCellDto;
import vn.ptit.ltm.common.enums.MatchParticipantStatus;
import vn.ptit.ltm.common.enums.PieceState;
import vn.ptit.ltm.common.enums.RoomState;
import vn.ptit.ltm.common.enums.SpecialCellType;
import vn.ptit.ltm.common.enums.TurnState;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.common.model.BoardConstants;
import vn.ptit.ltm.common.model.BoardCoordinates;
import vn.ptit.ltm.common.model.GameConstants;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class GameEngine {
    private static final BigDecimal FIRST_PLACE_SCORE = new BigDecimal("3.0");
    private static final BigDecimal SECOND_PLACE_SCORE = new BigDecimal("1.5");
    private static final BigDecimal THIRD_PLACE_SCORE = new BigDecimal("0.5");
    private static final BigDecimal ZERO_SCORE = BigDecimal.ZERO;

    private GameEngine() {
    }

    public static RollOutcome rollDice(
            GameStateDto state,
            String playerId,
            DiceRoller diceRoller,
            Instant now
    ) {
        Objects.requireNonNull(diceRoller, "diceRoller");
        validateRoll(state, playerId, now);
        return rollDice(state, playerId, diceRoller.roll(), now);
    }

    public static RollOutcome rollDice(
            GameStateDto state,
            String playerId,
            int diceValue,
            Instant now
    ) {
        validateRoll(state, playerId, now);
        if (diceValue < GameConstants.DICE_MIN || diceValue > GameConstants.DICE_MAX) {
            throw new IllegalArgumentException("Dice value must be between 1 and 6");
        }

        MatchParticipantDto participant = currentParticipant(state);
        List<String> validPieceIds = calculateValidPieceIds(state, participant, diceValue);
        DiceResultDto diceResult = new DiceResultDto(
                state.roomId(),
                playerId,
                diceValue,
                validPieceIds
        );
        GameStateDto nextState;
        if (validPieceIds.isEmpty()) {
            nextState = beginNextTurn(state, now, state.stateVersion() + 1);
        } else {
            nextState = copyState(
                    state,
                    RoomState.PLAYING,
                    state.currentPlayerId(),
                    state.currentSlot(),
                    TurnState.WAITING_FOR_MOVE,
                    diceValue,
                    validPieceIds,
                    GameConstants.MOVE_PHASE_DURATION_MILLIS,
                    now.toEpochMilli() + GameConstants.MOVE_PHASE_DURATION_MILLIS,
                    state.participants(),
                    state.stateVersion() + 1
            );
        }
        return new RollOutcome(diceResult, nextState);
    }

    public static MoveOutcome movePiece(
            GameStateDto state,
            String playerId,
            String pieceId,
            Instant now
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(pieceId, "pieceId");
        Objects.requireNonNull(now, "now");
        requirePlaying(state);
        requireCurrentPlayer(state, playerId);
        if (state.turnState() != TurnState.WAITING_FOR_MOVE || state.diceValue() == null) {
            throw new GameException(ErrorCode.MOVE_NOT_ALLOWED, "The current phase does not allow moving");
        }

        PieceLocation selected = findPiece(state.participants(), pieceId);
        if (selected == null) {
            throw new GameException(ErrorCode.PIECE_NOT_FOUND, "Piece does not exist");
        }
        if (!selected.piece().ownerPlayerId().equals(playerId)) {
            throw new GameException(ErrorCode.INVALID_MOVE, "A player can only move their own piece");
        }
        if (selected.piece().state() == PieceState.FINISHED) {
            throw new GameException(ErrorCode.PIECE_ALREADY_FINISHED, "Finished pieces cannot move");
        }
        if (!state.validPieceIds().contains(pieceId)
                || !isValidMove(state, selected.piece(), state.diceValue())) {
            throw new GameException(ErrorCode.INVALID_MOVE, "Piece is not valid for the current dice value");
        }

        List<MatchParticipantDto> participants = new ArrayList<>(state.participants());
        PieceDto original = selected.piece();
        int actualSteps = actualSteps(original, state.diceValue());
        int targetStep = original.state() == PieceState.IN_YARD
                ? BoardConstants.FIRST_TRACK_STEP
                : original.stepCount() + actualSteps;
        PieceDto movedPiece = consumeSlow(original);
        String capturedPieceId = null;
        boolean shieldConsumed = false;
        SpecialCellType triggeredEffect = null;
        boolean luckyBonus = false;
        boolean landed = false;

        if (actualSteps > 0 || original.state() == PieceState.IN_YARD) {
            DestinationResolution baseMove = resolveDestination(
                    participants,
                    movedPiece,
                    targetStep,
                    pieceId
            );
            movedPiece = baseMove.piece();
            capturedPieceId = baseMove.capturedPieceId();
            shieldConsumed = baseMove.shieldConsumed();
            landed = baseMove.landed();
        }
        replacePiece(participants, selected, movedPiece);

        if (landed && movedPiece.state() == PieceState.ON_TRACK) {
            int landedCell = BoardCoordinates.toGlobalCell(movedPiece.color(), movedPiece.stepCount());
            SpecialCellDto specialCell = findSpecialCell(state, landedCell);
            if (specialCell != null) {
                triggeredEffect = specialCell.type();
                switch (specialCell.type()) {
                    case SLOW -> movedPiece = withSlow(movedPiece, true);
                    case SHIELD -> movedPiece = withShield(movedPiece, true);
                    case LUCKY -> luckyBonus = true;
                    case SPEED, TRAP -> {
                        int displacement = specialCell.type() == SpecialCellType.SPEED ? 2 : -2;
                        int effectTarget = movedPiece.stepCount() + displacement;
                        if (isEffectDestinationValid(participants, movedPiece, effectTarget)) {
                            DestinationResolution effectMove = resolveDestination(
                                    participants,
                                    movedPiece,
                                    effectTarget,
                                    pieceId
                            );
                            movedPiece = effectMove.piece();
                            if (effectMove.capturedPieceId() != null) {
                                capturedPieceId = effectMove.capturedPieceId();
                            }
                            shieldConsumed |= effectMove.shieldConsumed();
                        }
                    }
                }
                replacePiece(participants, findPiece(participants, pieceId), movedPiece);
            }
        }

        MatchParticipantDto movedParticipant = participants.get(selected.participantIndex());
        if (movedParticipant.matchStatus() == MatchParticipantStatus.ACTIVE
                && movedParticipant.pieces().stream().allMatch(piece -> piece.state() == PieceState.FINISHED)) {
            participants.set(
                    selected.participantIndex(),
                    completeParticipant(movedParticipant, nextBestRank(participants), participants.size())
            );
        }

        participants = finishIfOnlyOneActive(participants);
        boolean gameFinished = participants.stream()
                .noneMatch(participant -> participant.matchStatus() == MatchParticipantStatus.ACTIVE);
        MatchParticipantDto currentAfterMove = participants.get(selected.participantIndex());
        boolean bonusRoll = !gameFinished
                && currentAfterMove.matchStatus() == MatchParticipantStatus.ACTIVE
                && (state.diceValue() == GameConstants.SPAWN_DICE_VALUE || luckyBonus);

        GameStateDto nextState;
        if (gameFinished) {
            nextState = copyState(
                    state,
                    RoomState.FINISHED,
                    null,
                    null,
                    TurnState.FINISHED,
                    null,
                    List.of(),
                    0L,
                    null,
                    participants,
                    state.stateVersion() + 1
            );
        } else if (bonusRoll) {
            nextState = beginRollPhase(
                    state,
                    currentAfterMove,
                    participants,
                    now,
                    state.stateVersion() + 1
            );
        } else {
            nextState = beginNextTurn(
                    state,
                    participants,
                    now,
                    state.stateVersion() + 1
            );
        }

        PieceDto resultPiece = findPiece(nextState.participants(), pieceId).piece();
        MovePieceResultDto result = new MovePieceResultDto(
                resultPiece,
                capturedPieceId,
                triggeredEffect,
                shieldConsumed,
                bonusRoll,
                nextState
        );
        return new MoveOutcome(result, nextState);
    }

    public static List<String> calculateValidPieceIds(
            GameStateDto state,
            MatchParticipantDto participant,
            int diceValue
    ) {
        return participant.pieces().stream()
                .filter(piece -> isValidMove(state, piece, diceValue))
                .map(PieceDto::pieceId)
                .toList();
    }

    public static GameStateDto timeoutTurn(GameStateDto state, Instant now) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(now, "now");
        requirePlaying(state);
        if (state.turnState() != TurnState.WAITING_FOR_ROLL
                && state.turnState() != TurnState.WAITING_FOR_MOVE) {
            throw new GameException(ErrorCode.INVALID_GAME_STATE, "Only an active turn phase can time out");
        }
        return beginNextTurn(state, now, state.stateVersion() + 1);
    }

    private static boolean isValidMove(GameStateDto state, PieceDto piece, int diceValue) {
        if (piece.state() == PieceState.FINISHED) {
            return false;
        }
        if (piece.state() == PieceState.IN_YARD && diceValue != GameConstants.SPAWN_DICE_VALUE) {
            return false;
        }
        int steps = actualSteps(piece, diceValue);
        int targetStep = piece.state() == PieceState.IN_YARD
                ? BoardConstants.FIRST_TRACK_STEP
                : piece.stepCount() + steps;
        if (targetStep > BoardConstants.LAST_FINISH_STEP) {
            return false;
        }
        if (targetStep <= BoardConstants.LAST_RING_STEP) {
            int targetCell = BoardCoordinates.toGlobalCell(piece.color(), targetStep);
            PieceLocation occupant = findRingOccupant(state.participants(), targetCell, piece.pieceId());
            return occupant == null || !occupant.piece().ownerPlayerId().equals(piece.ownerPlayerId());
        }
        return state.participants().stream()
                .filter(participant -> participant.playerId().equals(piece.ownerPlayerId()))
                .flatMap(participant -> participant.pieces().stream())
                .filter(other -> !other.pieceId().equals(piece.pieceId()))
                .noneMatch(other -> other.state() == PieceState.IN_FINISH_TRACK
                        && other.stepCount() == targetStep);
    }

    private static int actualSteps(PieceDto piece, int diceValue) {
        return piece.slowed() ? Math.max(0, diceValue - 2) : diceValue;
    }

    private static PieceDto consumeSlow(PieceDto piece) {
        if (!piece.slowed()) {
            return piece;
        }
        return new PieceDto(
                piece.pieceId(),
                piece.ownerPlayerId(),
                piece.color(),
                piece.state(),
                piece.stepCount(),
                false,
                piece.shielded()
        );
    }

    private static PieceDto moveToStep(PieceDto piece, int targetStep) {
        PieceState targetState;
        boolean shielded = piece.shielded();
        if (targetStep <= BoardConstants.LAST_RING_STEP) {
            targetState = PieceState.ON_TRACK;
        } else if (targetStep < BoardConstants.LAST_FINISH_STEP) {
            targetState = PieceState.IN_FINISH_TRACK;
            shielded = false;
        } else {
            targetState = PieceState.FINISHED;
            shielded = false;
        }
        return new PieceDto(
                piece.pieceId(),
                piece.ownerPlayerId(),
                piece.color(),
                targetState,
                targetStep,
                false,
                shielded
        );
    }

    private static PieceDto sendToYard(PieceDto piece) {
        return new PieceDto(
                piece.pieceId(),
                piece.ownerPlayerId(),
                piece.color(),
                PieceState.IN_YARD,
                BoardConstants.YARD_STEP,
                false,
                false
        );
    }

    private static PieceDto withShield(PieceDto piece, boolean shielded) {
        return new PieceDto(
                piece.pieceId(),
                piece.ownerPlayerId(),
                piece.color(),
                piece.state(),
                piece.stepCount(),
                piece.slowed(),
                shielded
        );
    }

    private static PieceDto withSlow(PieceDto piece, boolean slowed) {
        return new PieceDto(
                piece.pieceId(),
                piece.ownerPlayerId(),
                piece.color(),
                piece.state(),
                piece.stepCount(),
                slowed,
                piece.shielded()
        );
    }

    private static SpecialCellDto findSpecialCell(GameStateDto state, int globalCell) {
        return state.specialCells().stream()
                .filter(cell -> cell.globalIndex() == globalCell)
                .findFirst()
                .orElse(null);
    }

    private static boolean isEffectDestinationValid(
            List<MatchParticipantDto> participants,
            PieceDto piece,
            int targetStep
    ) {
        if (targetStep < BoardConstants.FIRST_TRACK_STEP
                || targetStep > BoardConstants.LAST_FINISH_STEP) {
            return false;
        }
        if (targetStep <= BoardConstants.LAST_RING_STEP) {
            int targetCell = BoardCoordinates.toGlobalCell(piece.color(), targetStep);
            PieceLocation occupant = findRingOccupant(participants, targetCell, piece.pieceId());
            return occupant == null || !occupant.piece().ownerPlayerId().equals(piece.ownerPlayerId());
        }
        return participants.stream()
                .filter(participant -> participant.playerId().equals(piece.ownerPlayerId()))
                .flatMap(participant -> participant.pieces().stream())
                .filter(other -> !other.pieceId().equals(piece.pieceId()))
                .noneMatch(other -> other.state() == PieceState.IN_FINISH_TRACK
                        && other.stepCount() == targetStep);
    }

    private static DestinationResolution resolveDestination(
            List<MatchParticipantDto> participants,
            PieceDto movingPiece,
            int targetStep,
            String excludedPieceId
    ) {
        if (targetStep > BoardConstants.LAST_RING_STEP) {
            return new DestinationResolution(moveToStep(movingPiece, targetStep), null, false, true);
        }
        int targetCell = BoardCoordinates.toGlobalCell(movingPiece.color(), targetStep);
        PieceLocation occupant = findRingOccupant(participants, targetCell, excludedPieceId);
        if (occupant != null && occupant.piece().shielded()) {
            replacePiece(participants, occupant, withShield(occupant.piece(), false));
            return new DestinationResolution(movingPiece, null, true, false);
        }
        String capturedPieceId = null;
        if (occupant != null) {
            capturedPieceId = occupant.piece().pieceId();
            replacePiece(participants, occupant, sendToYard(occupant.piece()));
        }
        return new DestinationResolution(
                moveToStep(movingPiece, targetStep),
                capturedPieceId,
                false,
                true
        );
    }

    private static void replacePiece(
            List<MatchParticipantDto> participants,
            PieceLocation location,
            PieceDto replacement
    ) {
        MatchParticipantDto participant = participants.get(location.participantIndex());
        List<PieceDto> pieces = new ArrayList<>(participant.pieces());
        pieces.set(location.pieceIndex(), replacement);
        participants.set(location.participantIndex(), new MatchParticipantDto(
                participant.playerId(),
                participant.displayName(),
                participant.slotIndex(),
                participant.color(),
                participant.presenceState(),
                participant.matchStatus(),
                participant.rank(),
                participant.scoreEarned(),
                pieces
        ));
    }

    private static MatchParticipantDto completeParticipant(
            MatchParticipantDto participant,
            int rank,
            int playerCount
    ) {
        return new MatchParticipantDto(
                participant.playerId(),
                participant.displayName(),
                participant.slotIndex(),
                participant.color(),
                participant.presenceState(),
                MatchParticipantStatus.COMPLETED,
                rank,
                scoreForRank(playerCount, rank),
                participant.pieces()
        );
    }

    private static List<MatchParticipantDto> finishIfOnlyOneActive(
            List<MatchParticipantDto> participants
    ) {
        List<Integer> activeIndexes = new ArrayList<>();
        for (int index = 0; index < participants.size(); index++) {
            if (participants.get(index).matchStatus() == MatchParticipantStatus.ACTIVE) {
                activeIndexes.add(index);
            }
        }
        if (activeIndexes.size() != 1) {
            return participants;
        }
        List<MatchParticipantDto> completed = new ArrayList<>(participants);
        int index = activeIndexes.getFirst();
        completed.set(
                index,
                completeParticipant(participants.get(index), nextBestRank(participants), participants.size())
        );
        return completed;
    }

    private static int nextBestRank(List<MatchParticipantDto> participants) {
        Set<Integer> assigned = new HashSet<>();
        participants.stream()
                .map(MatchParticipantDto::rank)
                .filter(Objects::nonNull)
                .forEach(assigned::add);
        for (int rank = 1; rank <= participants.size(); rank++) {
            if (!assigned.contains(rank)) {
                return rank;
            }
        }
        throw new GameException(ErrorCode.INVALID_GAME_STATE, "No rank remains available");
    }

    private static BigDecimal scoreForRank(int playerCount, int rank) {
        if (rank == 1) {
            return FIRST_PLACE_SCORE;
        }
        if (rank == 2 && playerCount >= 3) {
            return SECOND_PLACE_SCORE;
        }
        if (rank == 3 && playerCount == 4) {
            return THIRD_PLACE_SCORE;
        }
        return ZERO_SCORE;
    }

    private static GameStateDto beginNextTurn(GameStateDto state, Instant now, long version) {
        return beginNextTurn(state, state.participants(), now, version);
    }

    private static GameStateDto beginNextTurn(
            GameStateDto state,
            List<MatchParticipantDto> participants,
            Instant now,
            long version
    ) {
        MatchParticipantDto next = findNextActive(participants, state.currentSlot());
        return beginRollPhase(state, next, participants, now, version);
    }

    private static GameStateDto beginRollPhase(
            GameStateDto state,
            MatchParticipantDto participant,
            List<MatchParticipantDto> participants,
            Instant now,
            long version
    ) {
        return copyState(
                state,
                RoomState.PLAYING,
                participant.playerId(),
                participant.slotIndex(),
                TurnState.WAITING_FOR_ROLL,
                null,
                List.of(),
                GameConstants.ROLL_PHASE_DURATION_MILLIS,
                now.toEpochMilli() + GameConstants.ROLL_PHASE_DURATION_MILLIS,
                participants,
                version
        );
    }

    private static MatchParticipantDto findNextActive(
            List<MatchParticipantDto> participants,
            int currentSlot
    ) {
        for (int offset = 1; offset <= BoardConstants.MAX_PLAYERS; offset++) {
            int candidateSlot = (currentSlot + offset) % BoardConstants.MAX_PLAYERS;
            for (MatchParticipantDto participant : participants) {
                if (participant.slotIndex() == candidateSlot
                        && participant.matchStatus() == MatchParticipantStatus.ACTIVE) {
                    return participant;
                }
            }
        }
        throw new GameException(ErrorCode.INVALID_GAME_STATE, "No ACTIVE participant is available");
    }

    private static PieceLocation findPiece(List<MatchParticipantDto> participants, String pieceId) {
        for (int participantIndex = 0; participantIndex < participants.size(); participantIndex++) {
            List<PieceDto> pieces = participants.get(participantIndex).pieces();
            for (int pieceIndex = 0; pieceIndex < pieces.size(); pieceIndex++) {
                if (pieces.get(pieceIndex).pieceId().equals(pieceId)) {
                    return new PieceLocation(participantIndex, pieceIndex, pieces.get(pieceIndex));
                }
            }
        }
        return null;
    }

    private static PieceLocation findRingOccupant(
            List<MatchParticipantDto> participants,
            int globalCell,
            String excludedPieceId
    ) {
        for (int participantIndex = 0; participantIndex < participants.size(); participantIndex++) {
            List<PieceDto> pieces = participants.get(participantIndex).pieces();
            for (int pieceIndex = 0; pieceIndex < pieces.size(); pieceIndex++) {
                PieceDto piece = pieces.get(pieceIndex);
                if (!piece.pieceId().equals(excludedPieceId)
                        && piece.state() == PieceState.ON_TRACK
                        && BoardCoordinates.toGlobalCell(piece.color(), piece.stepCount()) == globalCell) {
                    return new PieceLocation(participantIndex, pieceIndex, piece);
                }
            }
        }
        return null;
    }

    private static MatchParticipantDto currentParticipant(GameStateDto state) {
        return state.participants().stream()
                .filter(participant -> participant.playerId().equals(state.currentPlayerId()))
                .findFirst()
                .orElseThrow(() -> new GameException(
                        ErrorCode.INVALID_GAME_STATE,
                        "Current player is missing from participants"
                ));
    }

    private static void requirePlaying(GameStateDto state) {
        if (state.roomState() != RoomState.PLAYING || state.turnState() == TurnState.FINISHED) {
            throw new GameException(ErrorCode.GAME_NOT_STARTED, "Game is not currently playing");
        }
    }

    private static void requireCurrentPlayer(GameStateDto state, String playerId) {
        if (!playerId.equals(state.currentPlayerId())) {
            throw new GameException(ErrorCode.NOT_YOUR_TURN, "It is not this player's turn");
        }
    }

    private static void validateRoll(GameStateDto state, String playerId, Instant now) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(now, "now");
        requirePlaying(state);
        requireCurrentPlayer(state, playerId);
        if (state.turnState() != TurnState.WAITING_FOR_ROLL) {
            throw new GameException(ErrorCode.ROLL_NOT_ALLOWED, "The current phase does not allow rolling");
        }
    }

    private static GameStateDto copyState(
            GameStateDto source,
            RoomState roomState,
            String currentPlayerId,
            Integer currentSlot,
            TurnState turnState,
            Integer diceValue,
            List<String> validPieceIds,
            long phaseDurationMillis,
            Long serverDeadlineEpochMillis,
            List<MatchParticipantDto> participants,
            long stateVersion
    ) {
        return new GameStateDto(
                source.roomId(),
                source.matchId(),
                roomState,
                currentPlayerId,
                currentSlot,
                turnState,
                diceValue,
                validPieceIds,
                phaseDurationMillis,
                serverDeadlineEpochMillis,
                participants,
                source.specialCells(),
                stateVersion
        );
    }

    public record RollOutcome(DiceResultDto diceResult, GameStateDto gameState) {
        public RollOutcome {
            Objects.requireNonNull(diceResult, "diceResult");
            Objects.requireNonNull(gameState, "gameState");
        }
    }

    public record MoveOutcome(MovePieceResultDto result, GameStateDto gameState) {
        public MoveOutcome {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(gameState, "gameState");
        }
    }

    private record PieceLocation(int participantIndex, int pieceIndex, PieceDto piece) {
    }

    private record DestinationResolution(
            PieceDto piece,
            String capturedPieceId,
            boolean shieldConsumed,
            boolean landed
    ) {
    }
}
