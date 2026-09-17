package vn.ptit.ltm.server.repository;

import vn.ptit.ltm.common.dto.ranking.MatchHistoryEntryDto;
import vn.ptit.ltm.common.dto.ranking.RankingEntryDto;
import vn.ptit.ltm.common.enums.MatchParticipantStatus;
import vn.ptit.ltm.common.enums.PieceColor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JdbcMatchRepository implements MatchRepository {
    private static final String FIND_MATCH_ID = """
            SELECT id
            FROM matches
            WHERE public_id = ?
            FOR UPDATE
            """;
    private static final String INSERT_MATCH = """
            INSERT INTO matches (public_id, started_at, ended_at, status)
            VALUES (?, ?, ?, 'FINISHED')
            """;
    private static final String INSERT_MATCH_PLAYER = """
            INSERT INTO match_players (match_id, user_id, color, `rank`, score_earned, result)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String UPDATE_USER_STATISTICS = """
            UPDATE users
            SET score = score + ?,
                first_place_count = first_place_count + ?
            WHERE id = ?
            """;
    private static final String LOAD_PERSISTED_RESULT = """
            SELECT mp.user_id, u.display_name, mp.color, mp.`rank`, mp.score_earned,
                   u.score, u.first_place_count, mp.result
            FROM match_players mp
            JOIN users u ON u.id = mp.user_id
            WHERE mp.match_id = ?
            ORDER BY mp.`rank`
            """;
    private static final String FIND_RANKING = """
            SELECT id, display_name, score, first_place_count
            FROM users
            ORDER BY score DESC, first_place_count DESC, id ASC
            """;
    private static final String FIND_HISTORY = """
            SELECT m.public_id, m.started_at, m.ended_at,
                   (SELECT COUNT(*) FROM match_players counted WHERE counted.match_id = m.id) AS player_count,
                   mp.color, mp.`rank`, mp.score_earned, mp.result
            FROM match_players mp
            JOIN matches m ON m.id = mp.match_id
            WHERE mp.user_id = ? AND m.status = 'FINISHED'
            ORDER BY m.ended_at DESC, m.id DESC
            LIMIT ?
            """;

    private final DataSource dataSource;

    public JdbcMatchRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public PersistedMatchResult saveCompletedMatch(CompletedMatchRecord match) throws SQLException {
        Objects.requireNonNull(match, "match");
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Long existingId = findMatchId(connection, match.matchId());
                if (existingId != null) {
                    PersistedMatchResult existing = loadPersistedResult(connection, existingId, match.matchId());
                    connection.commit();
                    return existing;
                }

                long databaseMatchId = insertMatch(connection, match);
                for (CompletedMatchPlayerRecord player : match.players()) {
                    insertMatchPlayer(connection, databaseMatchId, player);
                    updateUserStatistics(connection, player);
                }
                PersistedMatchResult persisted = loadPersistedResult(
                        connection,
                        databaseMatchId,
                        match.matchId()
                );
                connection.commit();
                return persisted;
            } catch (SQLException exception) {
                rollback(connection, exception);
                if (isIntegrityViolation(exception)) {
                    PersistedMatchResult concurrentResult = findPersistedResult(match.matchId());
                    if (concurrentResult != null) {
                        return concurrentResult;
                    }
                }
                throw exception;
            } finally {
                restoreAutoCommit(connection, originalAutoCommit);
            }
        }
    }

    @Override
    public List<RankingEntryDto> findRanking() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_RANKING);
             ResultSet resultSet = statement.executeQuery()) {
            List<RankingEntryDto> ranking = new ArrayList<>();
            int rank = 1;
            while (resultSet.next()) {
                ranking.add(new RankingEntryDto(
                        rank++,
                        Long.toString(resultSet.getLong("id")),
                        resultSet.getString("display_name"),
                        resultSet.getBigDecimal("score"),
                        resultSet.getInt("first_place_count")
                ));
            }
            return List.copyOf(ranking);
        }
    }

    @Override
    public List<MatchHistoryEntryDto> findMatchHistory(long userId, int limit) throws SQLException {
        if (userId <= 0 || limit <= 0) {
            throw new IllegalArgumentException("userId and limit must be positive");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_HISTORY)) {
            statement.setLong(1, userId);
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<MatchHistoryEntryDto> history = new ArrayList<>();
                while (resultSet.next()) {
                    history.add(new MatchHistoryEntryDto(
                            resultSet.getString("public_id"),
                            resultSet.getTimestamp("started_at").toInstant().toEpochMilli(),
                            resultSet.getTimestamp("ended_at").toInstant().toEpochMilli(),
                            resultSet.getInt("player_count"),
                            PieceColor.valueOf(resultSet.getString("color")),
                            resultSet.getInt("rank"),
                            resultSet.getBigDecimal("score_earned"),
                            MatchParticipantStatus.FORFEITED.name().equals(resultSet.getString("result"))
                    ));
                }
                return List.copyOf(history);
            }
        }
    }

    private static Long findMatchId(Connection connection, String publicId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FIND_MATCH_ID)) {
            statement.setString(1, publicId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("id") : null;
            }
        }
    }

    private static long insertMatch(Connection connection, CompletedMatchRecord match) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                INSERT_MATCH,
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setString(1, match.matchId());
            statement.setTimestamp(2, Timestamp.from(match.startedAt()));
            statement.setTimestamp(3, Timestamp.from(match.endedAt()));
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new SQLException("Creating match did not return a generated key");
                }
                return generatedKeys.getLong(1);
            }
        }
    }

    private static void insertMatchPlayer(
            Connection connection,
            long matchId,
            CompletedMatchPlayerRecord player
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_MATCH_PLAYER)) {
            statement.setLong(1, matchId);
            statement.setLong(2, player.userId());
            statement.setString(3, player.color().name());
            statement.setInt(4, player.rank());
            statement.setBigDecimal(5, player.scoreEarned());
            statement.setString(6, player.status().name());
            statement.executeUpdate();
        }
    }

    private static void updateUserStatistics(Connection connection, CompletedMatchPlayerRecord player)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_USER_STATISTICS)) {
            statement.setBigDecimal(1, player.scoreEarned());
            statement.setInt(
                    2,
                    player.rank() == 1 && player.status() != MatchParticipantStatus.FORFEITED ? 1 : 0
            );
            statement.setLong(3, player.userId());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Match references an unknown user: " + player.userId());
            }
        }
    }

    private PersistedMatchResult findPersistedResult(String publicId) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            Long matchId = findMatchId(connection, publicId);
            return matchId == null ? null : loadPersistedResult(connection, matchId, publicId);
        }
    }

    private static PersistedMatchResult loadPersistedResult(
            Connection connection,
            long matchId,
            String publicId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOAD_PERSISTED_RESULT)) {
            statement.setLong(1, matchId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PersistedPlayerResult> players = new ArrayList<>();
                while (resultSet.next()) {
                    players.add(new PersistedPlayerResult(
                            resultSet.getLong("user_id"),
                            resultSet.getString("display_name"),
                            PieceColor.valueOf(resultSet.getString("color")),
                            resultSet.getInt("rank"),
                            resultSet.getBigDecimal("score_earned"),
                            resultSet.getBigDecimal("score"),
                            resultSet.getInt("first_place_count"),
                            MatchParticipantStatus.valueOf(resultSet.getString("result"))
                    ));
                }
                if (players.isEmpty()) {
                    throw new SQLException("Persisted match has no player results: " + publicId);
                }
                return new PersistedMatchResult(publicId, players);
            }
        }
    }

    private static boolean isIntegrityViolation(SQLException exception) {
        return "23000".equals(exception.getSQLState());
    }

    private static void rollback(Connection connection, SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean autoCommit) throws SQLException {
        if (connection.getAutoCommit() != autoCommit) {
            connection.setAutoCommit(autoCommit);
        }
    }
}
