package vn.ptit.ltm.server.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseConfigTest {
    @Test
    void usesDockerComposeCompatibleDefaults() {
        DatabaseConfig config = DatabaseConfig.fromEnvironment(Map.of());

        assertEquals(
                "jdbc:mysql://localhost:3306/ludo_game"
                        + "?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=SERVER",
                config.jdbcUrl()
        );
        assertEquals("ludo", config.username());
        assertEquals("ludo_dev_password", config.password());
        assertEquals(10, config.maximumPoolSize());
        assertEquals(Duration.ofSeconds(10), config.connectionTimeout());
    }

    @Test
    void databaseSpecificVariablesOverrideComposeVariables() {
        DatabaseConfig config = DatabaseConfig.fromEnvironment(Map.of(
                "MYSQL_DATABASE", "compose_db",
                "MYSQL_USER", "compose_user",
                "MYSQL_PASSWORD", "compose_password",
                "DB_NAME", "override_db",
                "DB_USER", "override_user",
                "DB_PASSWORD", "override_password",
                "DB_HOST", "db.internal",
                "DB_PORT", "3307",
                "DB_POOL_SIZE", "4",
                "DB_CONNECTION_TIMEOUT_MS", "2500"
        ));

        assertEquals(
                "jdbc:mysql://db.internal:3307/override_db"
                        + "?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=SERVER",
                config.jdbcUrl()
        );
        assertEquals("override_user", config.username());
        assertEquals("override_password", config.password());
        assertEquals(4, config.maximumPoolSize());
        assertEquals(Duration.ofMillis(2500), config.connectionTimeout());
    }

    @Test
    void neverExposesPasswordFromToString() {
        DatabaseConfig config = DatabaseConfig.fromEnvironment(Map.of("DB_PASSWORD", "very-secret"));

        assertFalse(config.toString().contains("very-secret"));
    }

    @Test
    void rejectsUnsafeDatabaseName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DatabaseConfig.fromEnvironment(Map.of("DB_NAME", "ludo_game?bad=true"))
        );
    }
}
