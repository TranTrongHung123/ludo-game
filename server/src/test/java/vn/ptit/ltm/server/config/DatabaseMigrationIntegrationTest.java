package vn.ptit.ltm.server.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.ptit.ltm.server.repository.JdbcUserRepository;
import vn.ptit.ltm.server.repository.UserAccountRecord;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TESTS", matches = "true")
class DatabaseMigrationIntegrationTest {
    @Test
    void migratesSchemaAndSupportsBasicUserRepository() throws Exception {
        DatabaseConfig config = DatabaseConfig.fromEnvironment();
        try (DatabaseManager database = DatabaseManager.initialize(config)) {
            assertTrue(tableNames(database).containsAll(
                    Set.of("flyway_schema_history", "match_players", "matches", "users")
            ));

            JdbcUserRepository repository = new JdbcUserRepository(database.dataSource());
            String username = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
            long userId = repository.create(username, "$2a$10$integration-test-hash", "Migration Test");
            try {
                UserAccountRecord user = repository.findByUsername(username).orElseThrow();
                assertEquals(userId, user.id());
                assertEquals("Migration Test", user.displayName());
                assertEquals("0.0", user.score().toPlainString());
            } finally {
                try (Connection connection = database.connection();
                     Statement statement = connection.createStatement()) {
                    assertEquals(1, statement.executeUpdate("DELETE FROM users WHERE id = " + userId));
                }
            }
        }
    }

    private static Set<String> tableNames(DatabaseManager database) throws Exception {
        Set<String> tables = new HashSet<>();
        try (Connection connection = database.connection();
             ResultSet resultSet = connection.getMetaData().getTables(
                     connection.getCatalog(),
                     null,
                     "%",
                     new String[]{"TABLE"}
             )) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("TABLE_NAME"));
            }
        }
        return tables;
    }
}
