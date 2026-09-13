package vn.ptit.ltm.common.dto.game;

import vn.ptit.ltm.common.enums.TurnState;

import java.util.Objects;

public record TurnTimeoutDto(String roomId, String playerId, TurnState timedOutState) {
    public TurnTimeoutDto {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(timedOutState, "timedOutState");
        if (timedOutState != TurnState.WAITING_FOR_ROLL && timedOutState != TurnState.WAITING_FOR_MOVE) {
            throw new IllegalArgumentException("Only a roll or move phase can time out");
        }
    }
}
