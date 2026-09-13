package vn.ptit.ltm.common.dto.session;

public record HeartbeatPayload(long serverTimeEpochMillis) {
    public HeartbeatPayload {
        if (serverTimeEpochMillis <= 0) {
            throw new IllegalArgumentException("serverTimeEpochMillis must be positive");
        }
    }
}
