package vn.ptit.ltm.server.config;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public record DatabaseConfig(
        String jdbcUrl,
        String username,
        String password,
        int maximumPoolSize,
        Duration connectionTimeout
) {
    private static final Pattern DATABASE_NAME = Pattern.compile("[A-Za-z0-9_]+");
    private static final int DEFAULT_MYSQL_PORT = 3306;
    private static final int DEFAULT_POOL_SIZE = 10;
    private static final long DEFAULT_CONNECTION_TIMEOUT_MILLIS = 10_000;

    public DatabaseConfig {
        requireNonBlank(jdbcUrl, "jdbcUrl");
        requireNonBlank(username, "username");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(connectionTimeout, "connectionTimeout");
        if (maximumPoolSize <= 0) {
            throw new IllegalArgumentException("maximumPoolSize must be positive");
        }
        if (connectionTimeout.isNegative() || connectionTimeout.isZero()) {
            throw new IllegalArgumentException("connectionTimeout must be positive");
        }
    }

    public static DatabaseConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    public static DatabaseConfig fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");

        String database = firstNonBlank(environment, "DB_NAME", "MYSQL_DATABASE", "ludo_game");
        if (!DATABASE_NAME.matcher(database).matches()) {
            throw new IllegalArgumentException("Database name may only contain letters, digits, and underscores");
        }
        String host = firstNonBlank(environment, "DB_HOST", null, "localhost");
        int port = parsePositiveInt(
                firstNonBlank(environment, "DB_PORT", "MYSQL_PORT", Integer.toString(DEFAULT_MYSQL_PORT)),
                "database port"
        );
        if (port > 65_535) {
            throw new IllegalArgumentException("database port must not exceed 65535");
        }
        String defaultUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=SERVER";
        String jdbcUrl = firstNonBlank(environment, "DB_URL", null, defaultUrl);
        String username = firstNonBlank(environment, "DB_USER", "MYSQL_USER", "ludo");
        String password = firstPresent(environment, "DB_PASSWORD", "MYSQL_PASSWORD", "ludo_dev_password");
        int poolSize = parsePositiveInt(
                firstNonBlank(environment, "DB_POOL_SIZE", null, Integer.toString(DEFAULT_POOL_SIZE)),
                "DB_POOL_SIZE"
        );
        long timeoutMillis = parsePositiveLong(
                firstNonBlank(
                        environment,
                        "DB_CONNECTION_TIMEOUT_MS",
                        null,
                        Long.toString(DEFAULT_CONNECTION_TIMEOUT_MILLIS)
                ),
                "DB_CONNECTION_TIMEOUT_MS"
        );

        return new DatabaseConfig(
                jdbcUrl,
                username,
                password,
                poolSize,
                Duration.ofMillis(timeoutMillis)
        );
    }

    @Override
    public String toString() {
        return "DatabaseConfig[jdbcUrl=" + jdbcUrl
                + ", username=" + username
                + ", password=<redacted>"
                + ", maximumPoolSize=" + maximumPoolSize
                + ", connectionTimeout=" + connectionTimeout
                + "]";
    }

    private static String firstNonBlank(
            Map<String, String> environment,
            String primary,
            String secondary,
            String defaultValue
    ) {
        String value = environment.get(primary);
        if (value != null && !value.isBlank()) {
            return value;
        }
        if (secondary != null) {
            value = environment.get(secondary);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return defaultValue;
    }

    private static String firstPresent(
            Map<String, String> environment,
            String primary,
            String secondary,
            String defaultValue
    ) {
        if (environment.containsKey(primary)) {
            return Objects.requireNonNull(environment.get(primary));
        }
        if (environment.containsKey(secondary)) {
            return Objects.requireNonNull(environment.get(secondary));
        }
        return defaultValue;
    }

    private static int parsePositiveInt(String value, String label) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(label + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a valid integer", exception);
        }
    }

    private static long parsePositiveLong(String value, String label) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(label + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a valid integer", exception);
        }
    }

    private static void requireNonBlank(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
    }
}
