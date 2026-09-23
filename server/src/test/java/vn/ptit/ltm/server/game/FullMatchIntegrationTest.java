package vn.ptit.ltm.server.game;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.dto.game.GameStateDto;
import vn.ptit.ltm.common.dto.game.MatchParticipantDto;
import vn.ptit.ltm.common.dto.game.PieceDto;
import vn.ptit.ltm.common.enums.MatchParticipantStatus;
import vn.ptit.ltm.common.enums.PieceColor;
import vn.ptit.ltm.common.enums.PieceState;
import vn.ptit.ltm.common.enums.PlayerPresenceState;
import vn.ptit.ltm.common.enums.RoomState;
import vn.ptit.ltm.common.enums.TurnState;
import vn.ptit.ltm.common.model.BoardConstants;
import vn.ptit.ltm.common.model.GameConstants;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullMatchIntegrationTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void completesFullTwoPlayerMatchFromAllPiecesInYard() {
        assertFullMatch(2, List.of("3.0", "0"));
    }

    @Test
    void completesFullThreePlayerMatchFromAllPiecesInYard() {
        assertFullMatch(3, List.of("3.0", "1.5", "0"));
    }

    @Test
    void completesFullFourPlayerMatchFromAllPiecesInYard() {
        assertFullMatch(4, List.of("3.0", "1.5", "0.5", "0"));
    }

    private static void assertFullMatch(int playerCount, List<String> expectedScores) {
        GameStateDto game = initialState(playerCount);
        int completedMoves = 0;

        while (game.roomState() == RoomState.PLAYING) {
            String currentPlayerId = game.currentPlayerId();
            PieceDto nextPiece = game.participants().stream()
                    .filter(participant -> participant.playerId().equals(currentPlayerId))
                    .flatMap(participant -> participant.pieces().stream())
                    .filter(piece -> piece.state() == PieceState.IN_YARD)
                    .findFirst()
                    .orElseThrow();

            game = rollAndMove(game, currentPlayerId, nextPiece.pieceId(), 6);
            int step = 0;
            while (step < BoardConstants.LAST_FINISH_STEP) {
                int dice = Math.min(GameConstants.DICE_MAX, BoardConstants.LAST_FINISH_STEP - step);
                game = rollAndMove(game, currentPlayerId, nextPiece.pieceId(), dice);
                step += dice;
            }
            completedMoves++;
        }

        // Canonical cascading stops as soon as only one ACTIVE participant remains.
        // By then every earlier player finished four pieces and the final player finished three.
        assertEquals(playerCount * BoardConstants.PIECES_PER_PLAYER - 1, completedMoves);
        assertEquals(RoomState.FINISHED, game.roomState());
        assertEquals(TurnState.FINISHED, game.turnState());
        assertNull(game.currentPlayerId());
        assertNull(game.currentSlot());
        assertEquals(playerCount, game.participants().size());
        for (int index = 0; index < playerCount; index++) {
            MatchParticipantDto participant = game.participants().get(index);
            assertEquals(index + 1, participant.rank());
            assertEquals(0, new BigDecimal(expectedScores.get(index)).compareTo(participant.scoreEarned()));
            assertEquals(MatchParticipantStatus.COMPLETED, participant.matchStatus());
            if (index < playerCount - 1) {
                assertTrue(participant.pieces().stream().allMatch(piece ->
                        piece.state() == PieceState.FINISHED
                                && piece.stepCount() == BoardConstants.LAST_FINISH_STEP
                ));
            } else {
                assertEquals(
                        BoardConstants.PIECES_PER_PLAYER - 1,
                        participant.pieces().stream()
                                .filter(piece -> piece.state() == PieceState.FINISHED)
                                .count()
                );
            }
        }
    }

    private static GameStateDto rollAndMove(
            GameStateDto game,
            String playerId,
            String pieceId,
            int dice
    ) {
        Instant actionTime = START.plusMillis(game.stateVersion() + 1);
        GameEngine.RollOutcome roll = GameEngine.rollDice(game, playerId, dice, actionTime);
        assertTrue(roll.diceResult().validPieceIds().contains(pieceId));
        return GameEngine.movePiece(
                roll.gameState(),
                playerId,
                pieceId,
                actionTime.plusMillis(1)
        ).gameState();
    }

    private static GameStateDto initialState(int playerCount) {
        List<MatchParticipantDto> participants = new ArrayList<>();
        for (int slot = 0; slot < playerCount; slot++) {
            participants.add(participant(slot));
        }
        return new GameStateDto(
                "full-match-room-" + playerCount,
                "full-match-" + playerCount,
                RoomState.PLAYING,
                "1",
                0,
                TurnState.WAITING_FOR_ROLL,
                null,
                List.of(),
                GameConstants.ROLL_PHASE_DURATION_MILLIS,
                START.plusMillis(GameConstants.ROLL_PHASE_DURATION_MILLIS).toEpochMilli(),
                participants,
                SpecialCellLayout.canonical(),
                0L
        );
    }

    private static MatchParticipantDto participant(int slot) {
        String playerId = Integer.toString(slot + 1);
        PieceColor color = PieceColor.fromSlotIndex(slot);
        List<PieceDto> pieces = new ArrayList<>();
        for (int piece = 1; piece <= BoardConstants.PIECES_PER_PLAYER; piece++) {
            pieces.add(new PieceDto(
                    playerId + "-piece-" + piece,
                    playerId,
                    color,
                    PieceState.IN_YARD,
                    BoardConstants.YARD_STEP));
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
}
