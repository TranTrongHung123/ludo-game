package vn.ptit.ltm.common.dto.game;

import vn.ptit.ltm.common.model.BoardConstants;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record GameOverDto(String roomId, String matchId, List<MatchStandingDto> standings) {
    public GameOverDto {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(matchId, "matchId");
        standings = standings == null ? List.of() : List.copyOf(standings);
        if (standings.size() < BoardConstants.MIN_PLAYERS || standings.size() > BoardConstants.MAX_PLAYERS) {
            throw new IllegalArgumentException("Game-over standings must contain between 2 and 4 players");
        }
        if (new HashSet<>(standings.stream().map(MatchStandingDto::rank).toList()).size() != standings.size()) {
            throw new IllegalArgumentException("Game-over ranks must be unique");
        }
    }
}
