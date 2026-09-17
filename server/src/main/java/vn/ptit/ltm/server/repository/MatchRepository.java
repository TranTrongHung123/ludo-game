package vn.ptit.ltm.server.repository;

import vn.ptit.ltm.common.dto.ranking.MatchHistoryEntryDto;
import vn.ptit.ltm.common.dto.ranking.RankingEntryDto;

import java.sql.SQLException;
import java.util.List;

public interface MatchRepository {
    PersistedMatchResult saveCompletedMatch(CompletedMatchRecord match) throws SQLException;

    List<RankingEntryDto> findRanking() throws SQLException;

    List<MatchHistoryEntryDto> findMatchHistory(long userId, int limit) throws SQLException;
}
