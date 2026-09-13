package vn.ptit.ltm.common.model;

import java.util.Set;

public final class BoardConstants {
    public static final int RING_SIZE = 48;
    public static final int FINISH_TRACK_SIZE = 6;
    public static final int YARD_STEP = -1;
    public static final int FIRST_TRACK_STEP = 0;
    public static final int LAST_RING_STEP = 47;
    public static final int FIRST_FINISH_STEP = 48;
    public static final int LAST_FINISH_STEP = 53;
    public static final int PIECES_PER_PLAYER = 4;
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 4;
    public static final Set<Integer> SPECIAL_CELL_BLACKLIST =
            Set.of(0, 11, 12, 23, 24, 35, 36, 47);

    private BoardConstants() {
    }
}
