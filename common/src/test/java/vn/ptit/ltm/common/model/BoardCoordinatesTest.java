package vn.ptit.ltm.common.model;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.enums.PieceColor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardCoordinatesTest {
    @Test
    void mapsLocalStepsToCanonicalGlobalCells() {
        assertEquals(0, BoardCoordinates.toGlobalCell(PieceColor.RED, 0));
        assertEquals(47, BoardCoordinates.toGlobalCell(PieceColor.RED, 47));
        assertEquals(12, BoardCoordinates.toGlobalCell(PieceColor.BLUE, 0));
        assertEquals(11, BoardCoordinates.toGlobalCell(PieceColor.BLUE, 47));
        assertEquals(23, BoardCoordinates.toGlobalCell(PieceColor.YELLOW, 47));
        assertEquals(35, BoardCoordinates.toGlobalCell(PieceColor.GREEN, 47));
    }

    @Test
    void mapsAllFinishStepsToSlots() {
        for (int step = 48; step <= 53; step++) {
            assertEquals(step - 47, BoardCoordinates.toFinishTrackSlot(step));
        }
        assertThrows(IllegalArgumentException.class, () -> BoardCoordinates.toFinishTrackSlot(47));
        assertThrows(IllegalArgumentException.class, () -> BoardCoordinates.toFinishTrackSlot(54));
    }

    @Test
    void rejectsEverySpecialCellBlacklistEntry() {
        BoardConstants.SPECIAL_CELL_BLACKLIST.forEach(index ->
                assertFalse(BoardCoordinates.isSpecialCellAllowed(index)));
        assertTrue(BoardCoordinates.isSpecialCellAllowed(1));
        assertFalse(BoardCoordinates.isSpecialCellAllowed(-1));
        assertFalse(BoardCoordinates.isSpecialCellAllowed(48));
    }
}
