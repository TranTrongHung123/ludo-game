package vn.ptit.ltm.common.model;

import vn.ptit.ltm.common.enums.PieceColor;

import java.util.Objects;

public final class BoardCoordinates {
    private BoardCoordinates() {
    }

    public static int toGlobalCell(PieceColor color, int stepCount) {
        Objects.requireNonNull(color, "color");
        if (stepCount < BoardConstants.FIRST_TRACK_STEP
                || stepCount > BoardConstants.LAST_RING_STEP) {
            throw new IllegalArgumentException("Ring step must be between 0 and 47: " + stepCount);
        }
        return (color.startIndex() + stepCount) % BoardConstants.RING_SIZE;
    }

    public static int toFinishTrackSlot(int stepCount) {
        if (stepCount < BoardConstants.FIRST_FINISH_STEP
                || stepCount > BoardConstants.LAST_FINISH_STEP) {
            throw new IllegalArgumentException("Finish step must be between 48 and 53: " + stepCount);
        }
        return stepCount - BoardConstants.LAST_RING_STEP;
    }

    public static boolean isSpecialCellAllowed(int globalIndex) {
        return globalIndex >= 0
                && globalIndex < BoardConstants.RING_SIZE
                && !BoardConstants.SPECIAL_CELL_BLACKLIST.contains(globalIndex);
    }
}
