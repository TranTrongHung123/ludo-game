package vn.ptit.ltm.common.dto;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.dto.game.PieceDto;
import vn.ptit.ltm.common.dto.game.SpecialCellDto;
import vn.ptit.ltm.common.enums.PieceColor;
import vn.ptit.ltm.common.enums.PieceState;
import vn.ptit.ltm.common.enums.SpecialCellType;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameDtoInvariantTest {
    @Test
    void enforcesCanonicalPieceStateRanges() {
        assertDoesNotThrow(() -> piece(PieceState.IN_YARD, -1));
        assertDoesNotThrow(() -> piece(PieceState.ON_TRACK, 47));
        assertDoesNotThrow(() -> piece(PieceState.IN_FINISH_TRACK, 52));
        assertDoesNotThrow(() -> piece(PieceState.FINISHED, 53));

        assertThrows(IllegalArgumentException.class, () -> piece(PieceState.IN_YARD, 0));
        assertThrows(IllegalArgumentException.class, () -> piece(PieceState.ON_TRACK, 48));
        assertThrows(IllegalArgumentException.class, () -> piece(PieceState.IN_FINISH_TRACK, 53));
        assertThrows(IllegalArgumentException.class, () -> piece(PieceState.FINISHED, 54));
    }

    @Test
    void rejectsBlacklistedSpecialCell() {
        assertThrows(IllegalArgumentException.class,
                () -> new SpecialCellDto(12, SpecialCellType.SPEED));
        assertDoesNotThrow(() -> new SpecialCellDto(13, SpecialCellType.SPEED));
    }

    private static PieceDto piece(PieceState state, int stepCount) {
        return new PieceDto("piece-1", "player-1", PieceColor.RED, state, stepCount);
    }
}
