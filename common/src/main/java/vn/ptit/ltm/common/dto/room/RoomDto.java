package vn.ptit.ltm.common.dto.room;

import vn.ptit.ltm.common.enums.RoomState;
import vn.ptit.ltm.common.model.BoardConstants;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record RoomDto(
        String roomId,
        String hostPlayerId,
        RoomState state,
        List<RoomPlayerDto> players
) {
    public RoomDto {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(hostPlayerId, "hostPlayerId");
        Objects.requireNonNull(state, "state");
        players = players == null ? List.of() : List.copyOf(players);
        if (players.isEmpty()) {
            throw new IllegalArgumentException("A room snapshot must contain its host");
        }
        if (players.size() > BoardConstants.MAX_PLAYERS) {
            throw new IllegalArgumentException("A room cannot contain more than 4 players");
        }
        if (new HashSet<>(players.stream().map(RoomPlayerDto::slotIndex).toList()).size() != players.size()) {
            throw new IllegalArgumentException("Room player slots must be unique");
        }
        if (new HashSet<>(players.stream().map(RoomPlayerDto::playerId).toList()).size() != players.size()) {
            throw new IllegalArgumentException("Room player identifiers must be unique");
        }
        if (players.stream().noneMatch(player -> player.playerId().equals(hostPlayerId))) {
            throw new IllegalArgumentException("hostPlayerId must identify a room player");
        }
    }
}
