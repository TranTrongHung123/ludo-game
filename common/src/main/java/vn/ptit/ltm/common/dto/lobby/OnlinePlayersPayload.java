package vn.ptit.ltm.common.dto.lobby;

import vn.ptit.ltm.common.dto.player.PlayerSummaryDto;

import java.util.List;

public record OnlinePlayersPayload(List<PlayerSummaryDto> players) {
    public OnlinePlayersPayload {
        players = players == null ? List.of() : List.copyOf(players);
    }
}
