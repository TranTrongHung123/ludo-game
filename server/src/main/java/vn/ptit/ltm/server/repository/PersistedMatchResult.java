package vn.ptit.ltm.server.repository;

import java.util.List;
import java.util.Objects;

public record PersistedMatchResult(String matchId, List<PersistedPlayerResult> players) {
    public PersistedMatchResult {
        Objects.requireNonNull(matchId, "matchId");
        players = players == null ? List.of() : List.copyOf(players);
    }
}
