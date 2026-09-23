package vn.ptit.ltm.server.game;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.dto.game.GameStateDto;
import vn.ptit.ltm.common.dto.game.MatchParticipantDto;
import vn.ptit.ltm.common.dto.game.PieceDto;
import vn.ptit.ltm.common.dto.game.SpecialCellDto;
import vn.ptit.ltm.common.enums.MatchParticipantStatus;
import vn.ptit.ltm.common.enums.PieceColor;
import vn.ptit.ltm.common.enums.PieceState;
import vn.ptit.ltm.common.enums.PlayerPresenceState;
import vn.ptit.ltm.common.enums.RoomState;
import vn.ptit.ltm.common.enums.SpecialCellType;
import vn.ptit.ltm.common.enums.TurnState;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.common.model.BoardCoordinates;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameEngineTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void rollIsServerValidatedAndNoValidMoveImmediatelyRotatesTurn() {
        GameStateDto initial = state(participant("1", 0), participant("2", 1));

        GameException wrongTurn = assertThrows(
                GameException.class,
                () -> GameEngine.rollDice(initial, "2", 6, NOW)
        );
        assertEquals(ErrorCode.NOT_YOUR_TURN, wrongTurn.errorCode());

        var outcome = GameEngine.rollDice(initial, "1", 3, NOW);
        assertTrue(outcome.diceResult().validPieceIds().isEmpty());
        assertEquals("2", outcome.gameState().currentPlayerId());
        assertEquals(TurnState.WAITING_FOR_ROLL, outcome.gameState().turnState());
        assertNull(outcome.gameState().diceValue());
        assertEquals(1L, outcome.gameState().stateVersion());

        GameException duplicateRoll = assertThrows(
                GameException.class,
                () -> GameEngine.rollDice(
                        GameEngine.rollDice(initial, "1", 6, NOW).gameState(),
                        "1",
                        2,
                        NOW
                )
        );
        assertEquals(ErrorCode.ROLL_NOT_ALLOWED, duplicateRoll.errorCode());
    }

    @Test
    void turnRotationSkipsEmptyAndCompletedSlots() {
        MatchParticipantDto completed = completedParticipant("2", 1, 1);
        GameStateDto initial = state(
                participant("1", 0),
                completed,
                participant("3", 3)
        );

        var outcome = GameEngine.rollDice(initial, "1", 3, NOW);
        assertEquals("3", outcome.gameState().currentPlayerId());
        assertEquals(3, outcome.gameState().currentSlot());
    }

    @Test
    void rollingSixSpawnsPieceAndGrantsOneBonusRoll() {
        GameStateDto initial = state(participant("1", 0), participant("2", 1));
        var rolled = GameEngine.rollDice(initial, "1", 6, NOW);

        assertEquals(4, rolled.diceResult().validPieceIds().size());
        assertEquals(TurnState.WAITING_FOR_MOVE, rolled.gameState().turnState());
        assertEquals(6, rolled.gameState().diceValue());

        var moved = GameEngine.movePiece(rolled.gameState(), "1", "1-piece-1", NOW);
        assertEquals(PieceState.ON_TRACK, moved.result().piece().state());
        assertEquals(0, moved.result().piece().stepCount());
        assertTrue(moved.result().bonusRoll());
        assertEquals("1", moved.gameState().currentPlayerId());
        assertEquals(TurnState.WAITING_FOR_ROLL, moved.gameState().turnState());
        assertEquals(2L, moved.gameState().stateVersion());
    }

    @Test
    void ownPieceBlocksDestinationWhileOpponentIsCaptured() {
        MatchParticipantDto red = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 0)
        );
        GameStateDto blocked = state(red, participant("2", 1));
        var blockedRoll = GameEngine.rollDice(blocked, "1", 6, NOW);
        assertEquals(List.of("1-piece-1"), blockedRoll.diceResult().validPieceIds());

        MatchParticipantDto blue = participant(
                "2",
                1,
                piece("2", 1, PieceColor.BLUE, PieceState.ON_TRACK, 39)
        );
        GameStateDto captureState = state(red, blue);
        var rolled = GameEngine.rollDice(captureState, "1", 3, NOW);
        var moved = GameEngine.movePiece(rolled.gameState(), "1", "1-piece-1", NOW);

        assertEquals("2-piece-1", moved.result().capturedPieceId());
        assertEquals(3, moved.result().piece().stepCount());
        PieceDto captured = pieceById(moved.gameState(), "2-piece-1");
        assertEquals(PieceState.IN_YARD, captured.state());
        assertEquals(-1, captured.stepCount());
    }

    @Test
    void supportsCarryOverFinishTrackAndRejectsOvershoot() {
        MatchParticipantDto red = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 46)
        );
        var rolled = GameEngine.rollDice(state(red, participant("2", 1)), "1", 4, NOW);
        var moved = GameEngine.movePiece(rolled.gameState(), "1", "1-piece-1", NOW);
        assertEquals(PieceState.IN_FINISH_TRACK, moved.result().piece().state());
        assertEquals(50, moved.result().piece().stepCount());

        MatchParticipantDto nearFinish = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.IN_FINISH_TRACK, 52)
        );
        var overshoot = GameEngine.rollDice(state(nearFinish, participant("2", 1)), "1", 2, NOW);
        assertTrue(overshoot.diceResult().validPieceIds().isEmpty());
        assertEquals("2", overshoot.gameState().currentPlayerId());

        MatchParticipantDto occupiedFinish = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.IN_FINISH_TRACK, 48),
                piece("1", 2, PieceColor.RED, PieceState.IN_FINISH_TRACK, 51)
        );
        var occupied = GameEngine.rollDice(
                state(occupiedFinish, participant("2", 1)),
                "1",
                3,
                NOW
        );
        assertTrue(occupied.diceResult().validPieceIds().isEmpty());
    }

    @Test
    void finishingFourthPieceCompletesRankingAndEndsTwoPlayerGame() {
        MatchParticipantDto red = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.IN_FINISH_TRACK, 52),
                piece("1", 2, PieceColor.RED, PieceState.FINISHED, 53),
                piece("1", 3, PieceColor.RED, PieceState.FINISHED, 53),
                piece("1", 4, PieceColor.RED, PieceState.FINISHED, 53)
        );
        var rolled = GameEngine.rollDice(state(red, participant("2", 1)), "1", 1, NOW);
        var moved = GameEngine.movePiece(rolled.gameState(), "1", "1-piece-1", NOW);

        assertEquals(RoomState.FINISHED, moved.gameState().roomState());
        assertEquals(TurnState.FINISHED, moved.gameState().turnState());
        assertNull(moved.gameState().currentPlayerId());
        MatchParticipantDto winner = participantById(moved.gameState(), "1");
        MatchParticipantDto runnerUp = participantById(moved.gameState(), "2");
        assertEquals(MatchParticipantStatus.COMPLETED, winner.matchStatus());
        assertEquals(1, winner.rank());
        assertEquals(new BigDecimal("3.0"), winner.scoreEarned());
        assertEquals(MatchParticipantStatus.COMPLETED, runnerUp.matchStatus());
        assertEquals(2, runnerUp.rank());
        assertEquals(BigDecimal.ZERO, runnerUp.scoreEarned());
    }

    @Test
    void assignsCanonicalScoresForThreePlayerMatch() {
        GameStateDto game = state(
                nearCompletionParticipant("1", 0),
                nearCompletionParticipant("2", 1),
                participant("3", 2)
        );

        game = completeCurrentPlayer(game, "1");
        game = completeCurrentPlayer(game, "2");

        assertEquals(RoomState.FINISHED, game.roomState());
        assertEquals(new BigDecimal("3.0"), participantById(game, "1").scoreEarned());
        assertEquals(new BigDecimal("1.5"), participantById(game, "2").scoreEarned());
        assertEquals(BigDecimal.ZERO, participantById(game, "3").scoreEarned());
        assertEquals(1, participantById(game, "1").rank());
        assertEquals(2, participantById(game, "2").rank());
        assertEquals(3, participantById(game, "3").rank());
    }

    @Test
    void assignsCanonicalScoresForFourPlayerMatch() {
        GameStateDto game = state(
                nearCompletionParticipant("1", 0),
                nearCompletionParticipant("2", 1),
                nearCompletionParticipant("3", 2),
                participant("4", 3)
        );

        game = completeCurrentPlayer(game, "1");
        game = completeCurrentPlayer(game, "2");
        game = completeCurrentPlayer(game, "3");

        assertEquals(RoomState.FINISHED, game.roomState());
        assertEquals(new BigDecimal("3.0"), participantById(game, "1").scoreEarned());
        assertEquals(new BigDecimal("1.5"), participantById(game, "2").scoreEarned());
        assertEquals(new BigDecimal("0.5"), participantById(game, "3").scoreEarned());
        assertEquals(BigDecimal.ZERO, participantById(game, "4").scoreEarned());
    }

    @Test
    void forfeitRemovesPiecesAssignsWorstRankAndEndsTwoPlayerGame() {
        MatchParticipantDto red = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 10)
        );

        GameStateDto forfeited = GameEngine.forfeitParticipant(
                state(red, participant("2", 1)),
                "1",
                NOW
        );

        assertEquals(RoomState.FINISHED, forfeited.roomState());
        assertEquals(TurnState.FINISHED, forfeited.turnState());
        assertNull(forfeited.currentPlayerId());
        MatchParticipantDto loser = participantById(forfeited, "1");
        assertEquals(MatchParticipantStatus.FORFEITED, loser.matchStatus());
        assertEquals(2, loser.rank());
        assertEquals(BigDecimal.ZERO, loser.scoreEarned());
        assertTrue(loser.pieces().stream().allMatch(piece ->
                piece.state() == PieceState.IN_YARD
                        && piece.stepCount() == -1
        ));
        MatchParticipantDto winner = participantById(forfeited, "2");
        assertEquals(MatchParticipantStatus.COMPLETED, winner.matchStatus());
        assertEquals(1, winner.rank());
        assertEquals(new BigDecimal("3.0"), winner.scoreEarned());
    }

    @Test
    void forfeitPreservesAnotherPlayersPhaseAndSkipsForfeitedSlots() {
        GameStateDto initial = state(
                participant("1", 0),
                participant("2", 1),
                participant("3", 2),
                participant("4", 3)
        );

        GameStateDto nonCurrentForfeit = GameEngine.forfeitParticipant(initial, "3", NOW);
        assertEquals("1", nonCurrentForfeit.currentPlayerId());
        assertEquals(initial.turnState(), nonCurrentForfeit.turnState());
        assertEquals(initial.serverDeadlineEpochMillis(), nonCurrentForfeit.serverDeadlineEpochMillis());
        assertEquals(4, participantById(nonCurrentForfeit, "3").rank());

        Instant oneSecondLater = NOW.plusSeconds(1);
        GameStateDto currentForfeit = GameEngine.forfeitParticipant(
                nonCurrentForfeit,
                "1",
                oneSecondLater
        );
        assertEquals("2", currentForfeit.currentPlayerId());
        assertEquals(TurnState.WAITING_FOR_ROLL, currentForfeit.turnState());
        assertEquals(oneSecondLater.plusSeconds(120).toEpochMilli(), currentForfeit.serverDeadlineEpochMillis());
        assertEquals(3, participantById(currentForfeit, "1").rank());
        assertEquals(MatchParticipantStatus.FORFEITED, participantById(currentForfeit, "3").matchStatus());
    }

    @Test
    void batchForfeitDoesNotAwardWinToAnotherExpiredParticipant() {
        GameStateDto finished = GameEngine.forfeitParticipants(
                state(participant("1", 0), participant("2", 1)),
                List.of("1", "2"),
                NOW
        );

        assertEquals(RoomState.FINISHED, finished.roomState());
        assertTrue(finished.participants().stream().allMatch(participant ->
                participant.matchStatus() == MatchParticipantStatus.FORFEITED
                        && BigDecimal.ZERO.compareTo(participant.scoreEarned()) == 0
        ));
        assertEquals(
                2,
                finished.participants().stream().map(MatchParticipantDto::rank).distinct().count()
        );
    }

    @Test
    void canonicalSpecialCellsAreSymmetricUniqueAndAllowed() {
        List<SpecialCellDto> cells = SpecialCellLayout.canonical();

        assertEquals(16, cells.size());
        assertEquals(16, cells.stream().map(SpecialCellDto::globalIndex).distinct().count());
        assertTrue(cells.stream().allMatch(cell -> BoardCoordinates.isSpecialCellAllowed(cell.globalIndex())));
        for (SpecialCellType type : SpecialCellType.values()) {
            assertEquals(4, cells.stream().filter(cell -> cell.type() == type).count());
        }
    }

    @Test
    void speedMovesTwoExtraStepsWithoutChainingIntoAnotherSpecialCell() {
        MatchParticipantDto red = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 0)
        );
        var rolled = GameEngine.rollDice(stateWithSpecials(red, participant("2", 1)), "1", 2, NOW);
        var moved = GameEngine.movePiece(rolled.gameState(), "1", "1-piece-1", NOW);

        assertEquals(SpecialCellType.SPEED, moved.result().triggeredEffect());
        assertEquals(4, moved.result().piece().stepCount());
        assertEquals(0, moved.gameState().lastMove().fromStep());
        assertEquals(2, moved.gameState().lastMove().landedStep());
        assertEquals(4, moved.gameState().lastMove().toStep());
    }

    @Test
    void invalidSpeedDestinationKeepsPieceOnSpeedCell() {
        MatchParticipantDto red = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 0),
                piece("1", 2, PieceColor.RED, PieceState.ON_TRACK, 4)
        );
        var rolled = GameEngine.rollDice(stateWithSpecials(red, participant("2", 1)), "1", 2, NOW);
        var moved = GameEngine.movePiece(rolled.gameState(), "1", "1-piece-1", NOW);

        assertEquals(SpecialCellType.SPEED, moved.result().triggeredEffect());
        assertEquals(2, moved.result().piece().stepCount());
    }

    @Test
    void slowMovesBackImmediatelyWithoutChainingOrAffectingNextMove() {
        var red = participant("1", 0, piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 0));
        var rolled = GameEngine.rollDice(stateWithSpecials(red, participant("2", 1)), "1", 4, NOW);
        var moved = GameEngine.movePiece(rolled.gameState(), "1", "1-piece-1", NOW);
        assertEquals(SpecialCellType.SLOW, moved.result().triggeredEffect());
        assertEquals(2, moved.result().piece().stepCount());
        assertEquals(4, moved.gameState().lastMove().landedStep());
        assertEquals(2, moved.gameState().lastMove().toStep());
        assertEquals("2", moved.gameState().currentPlayerId());
        var next = GameEngine.rollDice(stateWithSpecials(participantById(moved.gameState(), "1"), participant("2", 1)), "1", 1, NOW);
        assertEquals(3, GameEngine.movePiece(next.gameState(), "1", "1-piece-1", NOW).result().piece().stepCount());
    }

    @Test
    void luckyGrantsOneFreshRollEvenWhenDiceIsSix() {
        for (int dice : new int[]{3, 6}) {
            var red = participant("1", 0, piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 6 - dice));
            var rolled = GameEngine.rollDice(stateWithSpecials(red, participant("2", 1)), "1", dice, NOW);
            var moved = GameEngine.movePiece(rolled.gameState(), "1", "1-piece-1", NOW);
            assertEquals(SpecialCellType.LUCKY, moved.result().triggeredEffect());
            assertTrue(moved.result().bonusRoll());
            assertEquals("1", moved.gameState().currentPlayerId());
            assertEquals(TurnState.WAITING_FOR_ROLL, moved.gameState().turnState());
            assertEquals(NOW.plusSeconds(120).toEpochMilli(), moved.gameState().serverDeadlineEpochMillis());
            var next = GameEngine.rollDice(moved.gameState(), "1", 1, NOW);
            assertEquals("2", GameEngine.movePiece(next.gameState(), "1", "1-piece-1", NOW).gameState().currentPlayerId());
        }
    }

    @Test
    void trapSendsEveryColorToYardAndRequiresSixToSpawnAgain() {
        for (PieceColor color : PieceColor.values()) {
            var owner = participant("1", color.slotIndex(), piece("1", 1, color, PieceState.ON_TRACK, 5));
            var opponent = participant("2", (color.slotIndex() + 1) % 4);
            var rolled = GameEngine.rollDice(stateWithSpecials(owner, opponent), "1", 3, NOW);
            var moved = GameEngine.movePiece(rolled.gameState(), "1", "1-piece-1", NOW);
            assertEquals(SpecialCellType.TRAP, moved.result().triggeredEffect());
            assertEquals(-1, moved.result().piece().stepCount());
            assertEquals(8, moved.gameState().lastMove().landedStep());
            assertEquals(-1, moved.gameState().lastMove().toStep());
            assertEquals(PieceState.IN_YARD, moved.result().piece().state());
            assertFalse(moved.result().bonusRoll());
            var reset = stateWithSpecials(participantById(moved.gameState(), "1"), opponent);
            assertTrue(GameEngine.rollDice(reset, "1", 5, NOW).diceResult().validPieceIds().isEmpty());
            assertEquals(0, GameEngine.movePiece(GameEngine.rollDice(reset, "1", 6, NOW).gameState(), "1", "1-piece-1", NOW).result().piece().stepCount());
        }
    }

    @Test
    void trapCapturesOccupantBeforeReturningMoverToYardAndPreservesSixBonus() {
        var red = participant("1", 0, piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 2));
        var blue = participant("2", 1, piece("2", 1, PieceColor.BLUE, PieceState.ON_TRACK, 44));
        var moved = GameEngine.movePiece(GameEngine.rollDice(stateWithSpecials(red, blue), "1", 6, NOW).gameState(), "1", "1-piece-1", NOW);
        assertEquals("2-piece-1", moved.result().capturedPieceId());
        assertEquals(-1, moved.result().piece().stepCount());
        assertEquals(-1, pieceById(moved.gameState(), "2-piece-1").stepCount());
        assertTrue(moved.result().bonusRoll());
    }

    @Test
    void displacementCapturesOpponentAndStopsAtOwnPiece() {
        for (SpecialCellType type : List.of(SpecialCellType.SPEED, SpecialCellType.SLOW)) {
            int destination = type == SpecialCellType.SPEED ? 6 : 2;
            var cells = List.of(new SpecialCellDto(4, type));
            var red = participant("1", 0, piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 0));
            var blue = participant("2", 1, piece("2", 1, PieceColor.BLUE, PieceState.ON_TRACK, destination + 36));
            var moved = GameEngine.movePiece(GameEngine.rollDice(stateWithCells(cells, red, blue), "1", 4, NOW).gameState(), "1", "1-piece-1", NOW);
            assertEquals(destination, moved.result().piece().stepCount());
            assertEquals("2-piece-1", moved.result().capturedPieceId());
            assertEquals(-1, pieceById(moved.gameState(), "2-piece-1").stepCount());
            var blocked = participant("1", 0, piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 0), piece("1", 2, PieceColor.RED, PieceState.ON_TRACK, destination));
            var stopped = GameEngine.movePiece(GameEngine.rollDice(stateWithCells(cells, blocked, participant("2", 1)), "1", 4, NOW).gameState(), "1", "1-piece-1", NOW);
            assertEquals(4, stopped.result().piece().stepCount());
        }
    }

    @Test
    void slowCannotMoveBeforeStartButTrapAlwaysReturnsToYard() {
        for (SpecialCellType type : List.of(SpecialCellType.SLOW, SpecialCellType.TRAP)) {
            var red = participant("1", 0, piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 0));
            var state = stateWithCells(List.of(new SpecialCellDto(1, type)), red, participant("2", 1));
            var moved = GameEngine.movePiece(GameEngine.rollDice(state, "1", 1, NOW).gameState(), "1", "1-piece-1", NOW);
            assertEquals(type == SpecialCellType.TRAP ? -1 : 1, moved.result().piece().stepCount());
        }
    }

    @Test
    void passingSpecialsAndLandingOnFormerShieldDoesNothing() {
        var red = participant("1", 0, piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 5));
        var moved = GameEngine.movePiece(GameEngine.rollDice(stateWithSpecials(red, participant("2", 1)), "1", 5, NOW).gameState(), "1", "1-piece-1", NOW);
        assertNull(moved.result().triggeredEffect());
        assertEquals(10, moved.result().piece().stepCount());
        assertFalse(moved.result().bonusRoll());
    }

    @Test
    void speedCanEnterFinishTrackUnlessOwnPieceBlocksIt() {
        for (boolean blocked : new boolean[]{false, true}) {
            var red = participant("1", 0,
                    piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 45),
                    piece("1", 2, PieceColor.RED, blocked ? PieceState.IN_FINISH_TRACK : PieceState.IN_YARD, blocked ? 48 : -1));
            var state = stateWithCells(List.of(new SpecialCellDto(46, SpecialCellType.SPEED)), red, participant("2", 1));
            var moved = GameEngine.movePiece(GameEngine.rollDice(state, "1", 1, NOW).gameState(), "1", "1-piece-1", NOW);
            assertEquals(blocked ? 46 : 48, moved.result().piece().stepCount());
            assertEquals(blocked ? PieceState.ON_TRACK : PieceState.IN_FINISH_TRACK, moved.result().piece().state());
        }
    }

    private static GameStateDto state(MatchParticipantDto... participants) {
        return stateWithCells(List.of(), participants);
    }

    private static GameStateDto stateWithSpecials(MatchParticipantDto... participants) {
        return stateWithCells(SpecialCellLayout.canonical(), participants);
    }

    private static GameStateDto stateWithCells(
            List<SpecialCellDto> specialCells,
            MatchParticipantDto... participants
    ) {
        MatchParticipantDto first = participants[0];
        return new GameStateDto(
                "room-1",
                "match-1",
                RoomState.PLAYING,
                first.playerId(),
                first.slotIndex(),
                TurnState.WAITING_FOR_ROLL,
                null,
                List.of(),
                120_000L,
                NOW.plusSeconds(120).toEpochMilli(),
                List.of(participants),
                specialCells,
                0L
        );
    }

    private static MatchParticipantDto participant(String playerId, int slot, PieceDto... overrides) {
        PieceColor color = PieceColor.fromSlotIndex(slot);
        List<PieceDto> pieces = new ArrayList<>();
        for (int index = 1; index <= 4; index++) {
            pieces.add(piece(playerId, index, color, PieceState.IN_YARD, -1));
        }
        for (PieceDto override : overrides) {
            int index = Integer.parseInt(override.pieceId().substring(override.pieceId().lastIndexOf('-') + 1));
            pieces.set(index - 1, override);
        }
        return new MatchParticipantDto(
                playerId,
                "Player " + playerId,
                slot,
                color,
                PlayerPresenceState.PLAYING,
                MatchParticipantStatus.ACTIVE,
                null,
                BigDecimal.ZERO,
                pieces
        );
    }

    private static MatchParticipantDto completedParticipant(String playerId, int slot, int rank) {
        PieceColor color = PieceColor.fromSlotIndex(slot);
        List<PieceDto> pieces = new ArrayList<>();
        for (int index = 1; index <= 4; index++) {
            pieces.add(piece(playerId, index, color, PieceState.FINISHED, 53));
        }
        return new MatchParticipantDto(
                playerId,
                "Player " + playerId,
                slot,
                color,
                PlayerPresenceState.PLAYING,
                MatchParticipantStatus.COMPLETED,
                rank,
                new BigDecimal("3.0"),
                pieces
        );
    }

    private static MatchParticipantDto nearCompletionParticipant(String playerId, int slot) {
        PieceColor color = PieceColor.fromSlotIndex(slot);
        return participant(
                playerId,
                slot,
                piece(playerId, 1, color, PieceState.IN_FINISH_TRACK, 52),
                piece(playerId, 2, color, PieceState.FINISHED, 53),
                piece(playerId, 3, color, PieceState.FINISHED, 53),
                piece(playerId, 4, color, PieceState.FINISHED, 53)
        );
    }

    private static GameStateDto completeCurrentPlayer(GameStateDto state, String playerId) {
        GameEngine.RollOutcome rolled = GameEngine.rollDice(state, playerId, 1, NOW);
        return GameEngine.movePiece(
                rolled.gameState(),
                playerId,
                playerId + "-piece-1",
                NOW
        ).gameState();
    }

    private static PieceDto piece(
            String playerId,
            int index,
            PieceColor color,
            PieceState state,
            int step
    ) {
        return new PieceDto(
                playerId + "-piece-" + index,
                playerId,
                color,
                state,
                step);
    }

    private static PieceDto pieceById(GameStateDto state, String pieceId) {
        return state.participants().stream()
                .flatMap(participant -> participant.pieces().stream())
                .filter(piece -> piece.pieceId().equals(pieceId))
                .findFirst()
                .orElseThrow();
    }

    private static MatchParticipantDto participantById(GameStateDto state, String playerId) {
        return state.participants().stream()
                .filter(participant -> participant.playerId().equals(playerId))
                .findFirst()
                .orElseThrow();
    }
}
