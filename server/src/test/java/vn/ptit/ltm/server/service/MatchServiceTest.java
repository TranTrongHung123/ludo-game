package vn.ptit.ltm.server.service;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.dto.ranking.MatchHistoryEntryDto;
import vn.ptit.ltm.common.dto.ranking.RankingEntryDto;
import vn.ptit.ltm.common.enums.MatchParticipantStatus;
import vn.ptit.ltm.common.enums.PieceColor;
import vn.ptit.ltm.server.repository.CompletedMatchPlayerRecord;
import vn.ptit.ltm.server.repository.CompletedMatchRecord;
import vn.ptit.ltm.server.repository.MatchRepository;
import vn.ptit.ltm.server.repository.PersistedMatchResult;
import vn.ptit.ltm.server.repository.PersistedPlayerResult;
import vn.ptit.ltm.server.repository.UserAccountRecord;
import vn.ptit.ltm.server.session.SessionManager;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchServiceTest {
    @Test
    void persistsResultAndRefreshesOnlineSessionStatistics() {
        FakeMatchRepository repository = new FakeMatchRepository();
        try (SessionManager sessions = new SessionManager()) {
            sessions.createSession(user(1, "Alpha"), "connection-1");
            sessions.createSession(user(2, "Beta"), "connection-2");
            MatchService service = new MatchService(repository, sessions);

            var gameOver = service.completeMatch("room-1", completedMatch());

            assertEquals("match-1", gameOver.matchId());
            assertEquals(new BigDecimal("13.0"), gameOver.standings().getFirst().totalScore());
            assertEquals(new BigDecimal("20.0"), gameOver.standings().get(1).totalScore());
            assertEquals(new BigDecimal("13.0"), sessions.findByUserId(1).orElseThrow().user().score());
            assertEquals(3, sessions.findByUserId(1).orElseThrow().user().firstPlaceCount());
        }
    }

    @Test
    void exposesBoundedHistoryAndRankingPayloads() {
        FakeMatchRepository repository = new FakeMatchRepository();
        try (SessionManager sessions = new SessionManager()) {
            MatchService matches = new MatchService(repository, sessions);
            RankingService ranking = new RankingService(repository);

            assertEquals(1, matches.matchHistory(1).matches().size());
            assertEquals(MatchService.MATCH_HISTORY_LIMIT, repository.requestedHistoryLimit);
            assertEquals("1", ranking.ranking().entries().getFirst().playerId());
        }
    }

    private static CompletedMatchRecord completedMatch() {
        return new CompletedMatchRecord(
                "match-1",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:10:00Z"),
                List.of(
                        new CompletedMatchPlayerRecord(
                                1,
                                "Alpha",
                                PieceColor.RED,
                                1,
                                new BigDecimal("3.0"),
                                MatchParticipantStatus.COMPLETED
                        ),
                        new CompletedMatchPlayerRecord(
                                2,
                                "Beta",
                                PieceColor.BLUE,
                                2,
                                BigDecimal.ZERO,
                                MatchParticipantStatus.FORFEITED
                        )
                )
        );
    }

    private static UserAccountRecord user(long id, String displayName) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new UserAccountRecord(
                id,
                "user" + id,
                "hash",
                displayName,
                id == 1 ? new BigDecimal("10.0") : new BigDecimal("20.0"),
                id == 1 ? 2 : 0,
                now,
                now
        );
    }

    private static final class FakeMatchRepository implements MatchRepository {
        private int requestedHistoryLimit;

        @Override
        public PersistedMatchResult saveCompletedMatch(CompletedMatchRecord match) {
            return new PersistedMatchResult(
                    match.matchId(),
                    List.of(
                            new PersistedPlayerResult(
                                    1,
                                    "Alpha",
                                    PieceColor.RED,
                                    1,
                                    new BigDecimal("3.0"),
                                    new BigDecimal("13.0"),
                                    3,
                                    MatchParticipantStatus.COMPLETED
                            ),
                            new PersistedPlayerResult(
                                    2,
                                    "Beta",
                                    PieceColor.BLUE,
                                    2,
                                    BigDecimal.ZERO,
                                    new BigDecimal("20.0"),
                                    0,
                                    MatchParticipantStatus.FORFEITED
                            )
                    )
            );
        }

        @Override
        public List<RankingEntryDto> findRanking() {
            return List.of(new RankingEntryDto(1, "1", "Alpha", new BigDecimal("13.0"), 3));
        }

        @Override
        public List<MatchHistoryEntryDto> findMatchHistory(long userId, int limit) throws SQLException {
            requestedHistoryLimit = limit;
            return List.of(new MatchHistoryEntryDto(
                    "match-1",
                    1_767_225_600_000L,
                    1_767_226_200_000L,
                    2,
                    PieceColor.RED,
                    1,
                    new BigDecimal("3.0"),
                    false
            ));
        }
    }
}
