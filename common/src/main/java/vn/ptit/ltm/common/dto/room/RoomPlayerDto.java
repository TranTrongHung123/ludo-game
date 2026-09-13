package vn.ptit.ltm.common.dto.room;

import vn.ptit.ltm.common.enums.PieceColor;
import vn.ptit.ltm.common.enums.PlayerPresenceState;
import vn.ptit.ltm.common.model.BoardConstants;

import java.util.Objects;

public record RoomPlayerDto(
        String playerId,
        String displayName,
        int slotIndex,
        PieceColor color,
        boolean ready,
        PlayerPresenceState presenceState
) {
    public RoomPlayerDto {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(presenceState, "presenceState");
        if (slotIndex < 0 || slotIndex >= BoardConstants.MAX_PLAYERS) {
            throw new IllegalArgumentException("slotIndex must be between 0 and 3");
        }
        if (color.slotIndex() != slotIndex) {
            throw new IllegalArgumentException("color must match slotIndex");
        }
    }
}
