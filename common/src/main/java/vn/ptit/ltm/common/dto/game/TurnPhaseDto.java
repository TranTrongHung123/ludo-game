package vn.ptit.ltm.common.dto.game;

import vn.ptit.ltm.common.enums.TurnState;

import java.util.Objects;

public record TurnPhaseDto(
        String roomId,
        String playerId,
        int slotIndex,
        TurnState turnState,
        long phaseDurationMillis,
        long serverDeadlineEpochMillis
) {
    public TurnPhaseDto {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(turnState, "turnState");
        if (slotIndex < 0 || slotIndex > 3 || phaseDurationMillis <= 0 || serverDeadlineEpochMillis <= 0) {
            throw new IllegalArgumentException("Invalid turn phase values");
        }
    }
}
