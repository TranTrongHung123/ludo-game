package vn.ptit.ltm.client.ui;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.dto.game.PieceDto;
import vn.ptit.ltm.common.enums.PieceColor;
import vn.ptit.ltm.common.enums.PieceState;
import vn.ptit.ltm.common.model.BoardConstants;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LudoBoardLayoutTest {
    @Test
    void createsFortyEightUniqueRingCoordinatesInsideBoard() {
        Set<LudoBoardLayout.BoardPoint> points = new HashSet<>();

        for (int index = 0; index < BoardConstants.RING_SIZE; index++) {
            LudoBoardLayout.BoardPoint point = LudoBoardLayout.ringPoint(index);
            assertInsideBoard(point);
            points.add(point);
        }

        assertEquals(BoardConstants.RING_SIZE, points.size());
    }

    @Test
    void createsSixDistinctFinishSlotsForEveryColor() {
        Set<LudoBoardLayout.BoardPoint> points = new HashSet<>();

        for (PieceColor color : PieceColor.values()) {
            double previousDistance = Double.MAX_VALUE;
            for (int slot = 1; slot <= BoardConstants.FINISH_TRACK_SIZE; slot++) {
                LudoBoardLayout.BoardPoint point = LudoBoardLayout.finishPoint(color, slot);
                double distance = distanceFromCenter(point);
                assertTrue(distance < previousDistance);
                previousDistance = distance;
                points.add(point);
            }
        }

        assertEquals(24, points.size());
    }

    @Test
    void mapsAuthoritativePieceCoordinatesWithoutMaintainingClientProgress() {
        PieceDto blueSpawn = new PieceDto(
                "blue-1", "player-2", PieceColor.BLUE, PieceState.ON_TRACK, 0, false, false
        );
        PieceDto redFinish = new PieceDto(
                "red-1", "player-1", PieceColor.RED, PieceState.IN_FINISH_TRACK, 50, false, false
        );

        assertEquals(
                LudoBoardLayout.ringPoint(PieceColor.BLUE.startIndex()),
                LudoBoardLayout.piecePoint(blueSpawn, 0)
        );
        assertEquals(
                LudoBoardLayout.finishPoint(PieceColor.RED, 3),
                LudoBoardLayout.piecePoint(redFinish, 0)
        );
    }

    @Test
    void createsFourUniqueYardPositionsForEveryColor() {
        Set<LudoBoardLayout.BoardPoint> points = new HashSet<>();

        for (PieceColor color : PieceColor.values()) {
            for (int pieceIndex = 0; pieceIndex < BoardConstants.PIECES_PER_PLAYER; pieceIndex++) {
                LudoBoardLayout.BoardPoint point = LudoBoardLayout.yardPoint(color, pieceIndex);
                assertInsideBoard(point);
                points.add(point);
            }
        }

        assertEquals(16, points.size());
    }

    @Test
    void rejectsCoordinatesOutsideCanonicalRanges() {
        assertThrows(IllegalArgumentException.class, () -> LudoBoardLayout.ringPoint(-1));
        assertThrows(IllegalArgumentException.class, () -> LudoBoardLayout.ringPoint(48));
        assertThrows(
                IllegalArgumentException.class,
                () -> LudoBoardLayout.finishPoint(PieceColor.RED, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> LudoBoardLayout.yardPoint(PieceColor.RED, 4)
        );
    }

    private static void assertInsideBoard(LudoBoardLayout.BoardPoint point) {
        assertTrue(point.x() >= 0.0 && point.x() <= LudoBoardLayout.BOARD_SIZE);
        assertTrue(point.y() >= 0.0 && point.y() <= LudoBoardLayout.BOARD_SIZE);
    }

    private static double distanceFromCenter(LudoBoardLayout.BoardPoint point) {
        double center = LudoBoardLayout.BOARD_SIZE / 2.0;
        return Math.hypot(point.x() - center, point.y() - center);
    }
}
