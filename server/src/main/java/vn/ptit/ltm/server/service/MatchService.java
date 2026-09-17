package vn.ptit.ltm.server.service;

import vn.ptit.ltm.common.dto.game.GameOverDto;
import vn.ptit.ltm.common.dto.game.MatchStandingDto;
import vn.ptit.ltm.common.dto.ranking.MatchHistoryPayload;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.server.repository.CompletedMatchRecord;
import vn.ptit.ltm.server.repository.MatchRepository;
import vn.ptit.ltm.server.repository.PersistedMatchResult;
import vn.ptit.ltm.server.session.SessionManager;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.Objects;

public final class MatchService {
    public static final int MATCH_HISTORY_LIMIT = 50;

    private final MatchRepository matchRepository;
    private final SessionManager sessionManager;

    public MatchService(MatchRepository matchRepository, SessionManager sessionManager) {
        this.matchRepository = Objects.requireNonNull(matchRepository, "matchRepository");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
    }

    public GameOverDto completeMatch(String roomId, CompletedMatchRecord match) {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(match, "match");
        try {
            PersistedMatchResult persisted = matchRepository.saveCompletedMatch(match);
            if (!persisted.matchId().equals(match.matchId())
                    || persisted.players().size() != match.players().size()
                    || !persisted.players().stream()
                    .map(player -> player.userId())
                    .collect(java.util.stream.Collectors.toSet())
                    .equals(match.players().stream()
                            .map(player -> player.userId())
                            .collect(java.util.stream.Collectors.toSet()))) {
                throw new ServiceException(
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        "Persisted match result does not match the completed game"
                );
            }
            persisted.players().forEach(player -> sessionManager.updateStatisticsForUser(
                    player.userId(),
                    player.totalScore(),
                    player.firstPlaceCount()
            ));
            return new GameOverDto(
                    roomId,
                    persisted.matchId(),
                    persisted.players().stream()
                            .sorted(Comparator.comparingInt(player -> player.rank()))
                            .map(player -> new MatchStandingDto(
                                    Long.toString(player.userId()),
                                    player.displayName(),
                                    player.color(),
                                    player.rank(),
                                    player.scoreEarned(),
                                    player.totalScore(),
                                    player.status()
                            ))
                            .toList()
            );
        } catch (SQLException exception) {
            throw new ServiceException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "Unable to persist match result",
                    exception
            );
        }
    }

    public MatchHistoryPayload matchHistory(long userId) {
        try {
            return new MatchHistoryPayload(
                    matchRepository.findMatchHistory(userId, MATCH_HISTORY_LIMIT)
            );
        } catch (SQLException exception) {
            throw new ServiceException(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "Unable to load match history",
                    exception
            );
        }
    }
}
