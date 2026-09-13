package vn.ptit.ltm.common.dto.session;

import vn.ptit.ltm.common.dto.game.GameStateDto;
import vn.ptit.ltm.common.dto.room.RoomDto;
import vn.ptit.ltm.common.enums.PlayerPresenceState;

import java.util.Objects;

public record ReconnectResult(
        boolean restored,
        PlayerPresenceState presenceState,
        RoomDto room,
        GameStateDto gameState
) {
    public ReconnectResult {
        Objects.requireNonNull(presenceState, "presenceState");
        if (!restored && (room != null || gameState != null)) {
            throw new IllegalArgumentException("A failed reconnect cannot include restored state");
        }
    }
}
