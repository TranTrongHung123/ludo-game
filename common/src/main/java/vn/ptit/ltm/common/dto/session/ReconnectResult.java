package vn.ptit.ltm.common.dto.session;

import vn.ptit.ltm.common.dto.game.GameStateDto;
import vn.ptit.ltm.common.dto.game.GameOverDto;
import vn.ptit.ltm.common.dto.room.RoomDto;
import vn.ptit.ltm.common.enums.PlayerPresenceState;

import java.util.Objects;

public record ReconnectResult(
        boolean restored,
        PlayerPresenceState presenceState,
        RoomDto room,
        GameStateDto gameState,
        GameOverDto gameOver
) {
    public ReconnectResult(boolean restored, PlayerPresenceState presenceState, RoomDto room, GameStateDto gameState) {
        this(restored, presenceState, room, gameState, null);
    }

    public ReconnectResult {
        Objects.requireNonNull(presenceState, "presenceState");
        if (!restored && (room != null || gameState != null || gameOver != null)) {
            throw new IllegalArgumentException("A failed reconnect cannot include restored state");
        }
        if (gameOver != null && (gameState == null || !gameOver.matchId().equals(gameState.matchId())
                || !gameOver.roomId().equals(gameState.roomId()))) {
            throw new IllegalArgumentException("Reconnect result must belong to the restored match");
        }
    }
}
