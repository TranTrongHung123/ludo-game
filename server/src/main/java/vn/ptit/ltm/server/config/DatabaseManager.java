package vn.ptit.ltm.server.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public final class DatabaseManager implements AutoCloseable {
    private final HikariDataSource dataSource;

    private DatabaseManager(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static DatabaseManager initialize(DatabaseConfig config) {
        Objects.requireNonNull(config, "config");
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("ludo-database-pool");
        hikariConfig.setJdbcUrl(config.jdbcUrl());
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setMaximumPoolSize(config.maximumPoolSize());
        hikariConfig.setMinimumIdle(Math.min(2, config.maximumPoolSize()));
        hikariConfig.setConnectionTimeout(config.connectionTimeout().toMillis());

        HikariDataSource dataSource = new HikariDataSource(hikariConfig);
        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .validateMigrationNaming(true)
                    .load()
                    .migrate();
            return new DatabaseManager(dataSource);
        } catch (RuntimeException exception) {
            dataSource.close();
            throw exception;
        }
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
