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
                piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 0, false, false)
        );
        GameStateDto blocked = state(red, participant("2", 1));
        var blockedRoll = GameEngine.rollDice(blocked, "1", 6, NOW);
        assertEquals(List.of("1-piece-1"), blockedRoll.diceResult().validPieceIds());

        MatchParticipantDto blue = participant(
                "2",
                1,
                piece("2", 1, PieceColor.BLUE, PieceState.ON_TRACK, 39, false, false)
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
    void shieldConsumesAttackAndKeepsAttackerAtOriginalStep() {
        MatchParticipantDto red = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 0, false, false)
        );
        MatchParticipantDto blue = participant(
                "2",
                1,
                piece("2", 1, PieceColor.BLUE, PieceState.ON_TRACK, 39, false, true)
        );
        var rolled = GameEngine.rollDice(state(red, blue), "1", 3, NOW);
        var moved = GameEngine.movePiece(rolled.gameState(), "1", "1-piece-1", NOW);

        assertTrue(moved.result().shieldConsumed());
        assertNull(moved.result().capturedPieceId());
        assertEquals(0, moved.result().piece().stepCount());
        assertFalse(pieceById(moved.gameState(), "2-piece-1").shielded());
    }

    @Test
    void supportsCarryOverFinishTrackAndRejectsOvershoot() {
        MatchParticipantDto red = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 46, false, true)
        );
        var rolled = GameEngine.rollDice(state(red, participant("2", 1)), "1", 4, NOW);
        var moved = GameEngine.movePiece(rolled.gameState(), "1", "1-piece-1", NOW);
        assertEquals(PieceState.IN_FINISH_TRACK, moved.result().piece().state());
        assertEquals(50, moved.result().piece().stepCount());
        assertFalse(moved.result().piece().shielded());

        MatchParticipantDto nearFinish = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.IN_FINISH_TRACK, 52, false, false)
        );
        var overshoot = GameEngine.rollDice(state(nearFinish, participant("2", 1)), "1", 2, NOW);
        assertTrue(overshoot.diceResult().validPieceIds().isEmpty());
        assertEquals("2", overshoot.gameState().currentPlayerId());

        MatchParticipantDto occupiedFinish = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.IN_FINISH_TRACK, 48, false, false),
                piece("1", 2, PieceColor.RED, PieceState.IN_FINISH_TRACK, 51, false, false)
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
                piece("1", 1, PieceColor.RED, PieceState.IN_FINISH_TRACK, 52, false, false),
                piece("1", 2, PieceColor.RED, PieceState.FINISHED, 53, false, false),
                piece("1", 3, PieceColor.RED, PieceState.FINISHED, 53, false, false),
                piece("1", 4, PieceColor.RED, PieceState.FINISHED, 53, false, false)
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
                piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 10, true, true)
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
                        && !piece.slowed()
                        && !piece.shielded()
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
        assertEquals(oneSecondLater.plusSeconds(8).toEpochMilli(), currentForfeit.serverDeadlineEpochMillis());
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

        assertEquals(20, cells.size());
        assertEquals(20, cells.stream().map(SpecialCellDto::globalIndex).distinct().count());
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
                piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 0, false, false)
        );
        var rolled = GameEngine.rollDice(stateWithSpecials(red, participant("2", 1)), "1", 2, NOW);
        var moved = GameEngine.movePiece(rolled.gameState(), "1", "1-piece-1", NOW);

        assertEquals(SpecialCellType.SPEED, moved.result().triggeredEffect());
        assertEquals(4, moved.result().piece().stepCount());
        assertFalse(moved.result().piece().slowed());
    }

    @Test
    void invalidSpeedDestinationKeepsPieceOnSpeedCell() {
        MatchParticipantDto red = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 0, false, false),
                piece("1", 2, PieceColor.RED, PieceState.ON_TRACK, 4, false, false)
        );
        var rolled = GameEngine.rollDice(stateWithSpecials(red, participant("2", 1)), "1", 2, NOW);
        var moved = GameEngine.movePiece(rolled.gameState(), "1", "1-piece-1", NOW);

        assertEquals(SpecialCellType.SPEED, moved.result().triggeredEffect());
        assertEquals(2, moved.result().piece().stepCount());
    }

    @Test
    void slowIsConsumedWhenAffectedPieceIsNextSelectedEvenAtZeroSteps() {
        MatchParticipantDto red = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 0, false, false)
        );
        var firstRoll = GameEngine.rollDice(stateWithSpecials(red, participant("2", 1)), "1", 4, NOW);
        var slowed = GameEngine.movePiece(firstRoll.gameState(), "1", "1-piece-1", NOW);
        assertEquals(SpecialCellType.SLOW, slowed.result().triggeredEffect());
        assertTrue(slowed.result().piece().slowed());

        MatchParticipantDto affected = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 4, true, false)
        );
        var secondRoll = GameEngine.rollDice(stateWithSpecials(affected, participant("2", 1)), "1", 2, NOW);
        var consumed = GameEngine.movePiece(secondRoll.gameState(), "1", "1-piece-1", NOW);
        assertEquals(4, consumed.result().piece().stepCount());
        assertFalse(consumed.result().piece().slowed());
        assertNull(consumed.result().triggeredEffect());
    }

    @Test
    void luckyGrantsOneBonusRollWithoutRollingSix() {
        MatchParticipantDto red = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 3, false, false)
        );
        var rolled = GameEngine.rollDice(stateWithSpecials(red, participant("2", 1)), "1", 3, NOW);
        var moved = GameEngine.movePiece(rolled.gameState(), "1", "1-piece-1", NOW);

        assertEquals(SpecialCellType.LUCKY, moved.result().triggeredEffect());
        assertTrue(moved.result().bonusRoll());
        assertEquals("1", moved.gameState().currentPlayerId());
        assertEquals(TurnState.WAITING_FOR_ROLL, moved.gameState().turnState());
    }

    @Test
    void trapMovesBackTwoStepsWithoutChaining() {
        MatchParticipantDto red = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 5, false, false)
        );
        var rolled = GameEngine.rollDice(stateWithSpecials(red, participant("2", 1)), "1", 3, NOW);
        var moved = GameEngine.movePiece(rolled.gameState(), "1", "1-piece-1", NOW);

        assertEquals(SpecialCellType.TRAP, moved.result().triggeredEffect());
        assertEquals(6, moved.result().piece().stepCount());
        assertFalse(moved.result().bonusRoll());
    }

    @Test
    void trapDestinationUsesNormalCaptureResolution() {
        MatchParticipantDto red = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 5, false, false)
        );
        MatchParticipantDto blue = participant(
                "2",
                1,
                piece("2", 1, PieceColor.BLUE, PieceState.ON_TRACK, 42, false, false)
        );
        var rolled = GameEngine.rollDice(stateWithSpecials(red, blue), "1", 3, NOW);
        var moved = GameEngine.movePiece(rolled.gameState(), "1", "1-piece-1", NOW);

        assertEquals(6, moved.result().piece().stepCount());
        assertEquals("2-piece-1", moved.result().capturedPieceId());
        assertEquals(PieceState.IN_YARD, pieceById(moved.gameState(), "2-piece-1").state());
    }

    @Test
    void speedDestinationConsumesOpponentShieldAndKeepsAttackerOnSpeedCell() {
        MatchParticipantDto red = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 0, false, false)
        );
        MatchParticipantDto blue = participant(
                "2",
                1,
                piece("2", 1, PieceColor.BLUE, PieceState.ON_TRACK, 40, false, true)
        );
        var rolled = GameEngine.rollDice(stateWithSpecials(red, blue), "1", 2, NOW);
        var moved = GameEngine.movePiece(rolled.gameState(), "1", "1-piece-1", NOW);

        assertEquals(SpecialCellType.SPEED, moved.result().triggeredEffect());
        assertTrue(moved.result().shieldConsumed());
        assertEquals(2, moved.result().piece().stepCount());
        assertFalse(pieceById(moved.gameState(), "2-piece-1").shielded());
    }

    @Test
    void trapNeverSendsPieceBackToYard() {
        MatchParticipantDto red = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 0, false, false)
        );
        GameStateDto custom = stateWithCells(
                List.of(new SpecialCellDto(1, SpecialCellType.TRAP)),
                red,
                participant("2", 1)
        );
        var rolled = GameEngine.rollDice(custom, "1", 1, NOW);
        var moved = GameEngine.movePiece(rolled.gameState(), "1", "1-piece-1", NOW);

        assertEquals(SpecialCellType.TRAP, moved.result().triggeredEffect());
        assertEquals(PieceState.ON_TRACK, moved.result().piece().state());
        assertEquals(1, moved.result().piece().stepCount());
    }

    @Test
    void shieldCellProtectsPieceAfterLanding() {
        MatchParticipantDto red = participant(
                "1",
                0,
                piece("1", 1, PieceColor.RED, PieceState.ON_TRACK, 5, false, false)
        );
        var rolled = GameEngine.rollDice(stateWithSpecials(red, participant("2", 1)), "1", 5, NOW);
        var moved = GameEngine.movePiece(rolled.gameState(), "1", "1-piece-1", NOW);

        assertEquals(SpecialCellType.SHIELD, moved.result().triggeredEffect());
        assertTrue(moved.result().piece().shielded());
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
                8_000L,
                NOW.plusSeconds(8).toEpochMilli(),
                List.of(participants),
                specialCells,
                0L
        );
    }

    private static MatchParticipantDto participant(String playerId, int slot, PieceDto... overrides) {
        PieceColor color = PieceColor.fromSlotIndex(slot);
        List<PieceDto> pieces = new ArrayList<>();
        for (int index = 1; index <= 4; index++) {
            pieces.add(piece(playerId, index, color, PieceState.IN_YARD, -1, false, false));
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
            pieces.add(piece(playerId, index, color, PieceState.FINISHED, 53, false, false));
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
                piece(playerId, 1, color, PieceState.IN_FINISH_TRACK, 52, false, false),
                piece(playerId, 2, color, PieceState.FINISHED, 53, false, false),
                piece(playerId, 3, color, PieceState.FINISHED, 53, false, false),
                piece(playerId, 4, color, PieceState.FINISHED, 53, false, false)
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
            int step,
            boolean slowed,
            boolean shielded
    ) {
        return new PieceDto(
                playerId + "-piece-" + index,
                playerId,
                color,
                state,
                step,
                slowed,
                shielded
        );
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
