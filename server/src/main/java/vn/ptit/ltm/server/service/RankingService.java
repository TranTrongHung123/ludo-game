package vn.ptit.ltm.server.service;

import vn.ptit.ltm.common.dto.ranking.RankingPayload;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.server.repository.MatchRepository;

import java.sql.SQLException;
import java.util.Objects;

public final class RankingService {
    private final MatchRepository matchRepository;

    public RankingService(MatchRepository matchRepository) {
        this.matchRepository = Objects.requireNonNull(matchRepository, "matchRepository");
    }

    public RankingPayload ranking() {
        try {
            return new RankingPayload(matchRepository.findRanking());
        } catch (SQLException exception) {
            throw new ServiceException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "Unable to load ranking",
                    exception
            );
        }
    }
}
