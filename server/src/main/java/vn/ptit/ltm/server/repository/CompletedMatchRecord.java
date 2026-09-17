package vn.ptit.ltm.server.repository;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record CompletedMatchRecord(
        String matchId,
        Instant startedAt,
        Instant endedAt,
        List<CompletedMatchPlayerRecord> players
) {
    public CompletedMatchRecord {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(endedAt, "endedAt");
        players = players == null ? List.of() : List.copyOf(players);
        if (matchId.isBlank()) {
            throw new IllegalArgumentException("matchId must not be blank");
        }
        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("endedAt must not be before startedAt");
        }
        if (players.size() < 2 || players.size() > 4) {
            throw new IllegalArgumentException("A completed match must contain between 2 and 4 players");
        }
        if (new HashSet<>(players.stream().map(CompletedMatchPlayerRecord::userId).toList()).size()
                != players.size()) {
            throw new IllegalArgumentException("Completed-match user IDs must be unique");
        }
        if (new HashSet<>(players.stream().map(CompletedMatchPlayerRecord::rank).toList()).size()
                != players.size()) {
            throw new IllegalArgumentException("Completed-match ranks must be unique");
        }
        int playerCount = players.size();
        if (players.stream().anyMatch(player -> player.rank() > playerCount)) {
            throw new IllegalArgumentException("Completed-match ranks must be within player count");
        }
        if (new HashSet<>(players.stream().map(CompletedMatchPlayerRecord::color).toList()).size()
                != players.size()) {
            throw new IllegalArgumentException("Completed-match colors must be unique");
        }
    }
}
