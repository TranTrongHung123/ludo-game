package vn.ptit.ltm.server.repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;

public final class JdbcUserRepository implements UserRepository {
    private static final String FIND_BY_USERNAME = """
            SELECT id, username, password_hash, display_name, score,
                   first_place_count, created_at, updated_at
            FROM users
            WHERE username = ?
            """;
    private static final String INSERT_USER = """
            INSERT INTO users (username, password_hash, display_name)
            VALUES (?, ?, ?)
            """;

    private final DataSource dataSource;

    public JdbcUserRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<UserAccountRecord> findByUsername(String username) throws SQLException {
        requireNonBlank(username, "username");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_USERNAME)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapUser(resultSet));
            }
        }
    }

    @Override
    public long create(String username, String passwordHash, String displayName) throws SQLException {
        requireNonBlank(username, "username");
        requireNonBlank(passwordHash, "passwordHash");
        requireNonBlank(displayName, "displayName");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     INSERT_USER,
                     Statement.RETURN_GENERATED_KEYS
             )) {
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.setString(3, displayName);
            statement.executeUpdate();
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new SQLException("Creating user did not return a generated key");
                }
                return generatedKeys.getLong(1);
            }
        }
    }

    private static UserAccountRecord mapUser(ResultSet resultSet) throws SQLException {
        return new UserAccountRecord(
                resultSet.getLong("id"),
                resultSet.getString("username"),
                resultSet.getString("password_hash"),
                resultSet.getString("display_name"),
                resultSet.getBigDecimal("score"),
                resultSet.getInt("first_place_count"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private static void requireNonBlank(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
    }
}
