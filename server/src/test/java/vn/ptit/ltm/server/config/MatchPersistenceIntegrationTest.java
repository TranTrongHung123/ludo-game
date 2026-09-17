package vn.ptit.ltm.server.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.ptit.ltm.common.enums.MatchParticipantStatus;
import vn.ptit.ltm.common.enums.PieceColor;
import vn.ptit.ltm.server.repository.CompletedMatchPlayerRecord;
import vn.ptit.ltm.server.repository.CompletedMatchRecord;
import vn.ptit.ltm.server.repository.JdbcMatchRepository;
import vn.ptit.ltm.server.repository.JdbcUserRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TESTS", matches = "true")
class MatchPersistenceIntegrationTest {
    @Test
    void savesCompletedMatchAtomicallyAndIdempotentlyWithRankingAndHistory() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String matchId = UUID.randomUUID().toString();
        List<Long> userIds = new ArrayList<>();

        try (DatabaseManager database = DatabaseManager.initialize(DatabaseConfig.fromEnvironment())) {
            JdbcUserRepository users = new JdbcUserRepository(database.dataSource());
            JdbcMatchRepository matches = new JdbcMatchRepository(database.dataSource());
            try {
                for (int index = 1; index <= 4; index++) {
                    userIds.add(users.create(
                            "match_" + index + "_" + suffix,
                            "$2a$10$integration-test-hash",
                            "Match Player " + index
                    ));
                }
                CompletedMatchRecord completed = completedMatch(matchId, userIds);

                var firstSave = matches.saveCompletedMatch(completed);
                var repeatedSave = matches.saveCompletedMatch(completed);

                assertEquals(firstSave, repeatedSave);
                assertEquals(new BigDecimal("3.0"), firstSave.players().getFirst().totalScore());
                assertEquals(1, firstSave.players().getFirst().firstPlaceCount());
                assertEquals(0, BigDecimal.ZERO.compareTo(firstSave.players().getLast().scoreEarned()));
                assertEquals(0, firstSave.players().getLast().firstPlaceCount());

                var history = matches.findMatchHistory(userIds.getFirst(), 50);
                assertEquals(1, history.size());
                assertEquals(matchId, history.getFirst().matchId());
                assertEquals(4, history.getFirst().playerCount());
                assertFalse(history.getFirst().forfeited());
                assertTrue(matches.findMatchHistory(userIds.getLast(), 50).getFirst().forfeited());

                var ranking = matches.findRanking();
                int firstIndex = indexOfPlayer(ranking, userIds.getFirst());
                int secondIndex = indexOfPlayer(ranking, userIds.get(1));
                int thirdIndex = indexOfPlayer(ranking, userIds.get(2));
                int fourthIndex = indexOfPlayer(ranking, userIds.getLast());
                assertTrue(firstIndex < secondIndex && secondIndex < thirdIndex && thirdIndex < fourthIndex);
            } finally {
                deleteMatchAndUsers(database, matchId, userIds);
            }
        }
    }

    private static CompletedMatchRecord completedMatch(String matchId, List<Long> userIds) {
        Instant startedAt = Instant.parse("2026-01-01T00:00:00Z");
        return new CompletedMatchRecord(
                matchId,
                startedAt,
                startedAt.plusSeconds(600),
                List.of(
                        player(userIds.get(0), 1, PieceColor.RED, "3.0", MatchParticipantStatus.COMPLETED),
                        player(userIds.get(1), 2, PieceColor.BLUE, "1.5", MatchParticipantStatus.COMPLETED),
                        player(userIds.get(2), 3, PieceColor.YELLOW, "0.5", MatchParticipantStatus.COMPLETED),
                        player(userIds.get(3), 4, PieceColor.GREEN, "0.0", MatchParticipantStatus.FORFEITED)
                )
        );
    }

    private static CompletedMatchPlayerRecord player(
            long userId,
            int rank,
            PieceColor color,
            String score,
            MatchParticipantStatus status
    ) {
        return new CompletedMatchPlayerRecord(
                userId,
                "Match Player " + rank,
                color,
                rank,
                new BigDecimal(score),
                status
        );
    }

    private static int indexOfPlayer(
            List<vn.ptit.ltm.common.dto.ranking.RankingEntryDto> ranking,
            long userId
    ) {
        for (int index = 0; index < ranking.size(); index++) {
            if (ranking.get(index).playerId().equals(Long.toString(userId))) {
                return index;
            }
        }
        return -1;
    }

    private static void deleteMatchAndUsers(
            DatabaseManager database,
            String matchId,
            List<Long> userIds
    ) throws Exception {
        try (Connection connection = database.connection()) {
            try (PreparedStatement deletePlayers = connection.prepareStatement("""
                    DELETE mp FROM match_players mp
                    JOIN matches m ON m.id = mp.match_id
                    WHERE m.public_id = ?
                    """)) {
                deletePlayers.setString(1, matchId);
                deletePlayers.executeUpdate();
            }
            try (PreparedStatement deleteMatch = connection.prepareStatement(
                    "DELETE FROM matches WHERE public_id = ?"
            )) {
                deleteMatch.setString(1, matchId);
                deleteMatch.executeUpdate();
            }
            try (PreparedStatement deleteUser = connection.prepareStatement("DELETE FROM users WHERE id = ?")) {
                for (Long userId : userIds) {
                    deleteUser.setLong(1, userId);
                    deleteUser.addBatch();
                }
                deleteUser.executeBatch();
            }
        }
    }
}
