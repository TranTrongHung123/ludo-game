package vn.ptit.ltm.common.model;

public final class GameConstants {
    public static final int DICE_MIN = 1;
    public static final int DICE_MAX = 6;
    public static final int SPAWN_DICE_VALUE = 6;
    public static final long ROLL_PHASE_DURATION_MILLIS = 120_000L;
    public static final long MOVE_PHASE_DURATION_MILLIS = 120_000L;
    public static final long RECONNECT_GRACE_PERIOD_MILLIS = 60_000L;
    public static final long INVITATION_TTL_MILLIS = 60_000L;

    private GameConstants() {
    }
}
