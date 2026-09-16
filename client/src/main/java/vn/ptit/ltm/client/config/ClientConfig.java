package vn.ptit.ltm.client.config;

import java.util.Map;
import java.util.Objects;

public record ClientConfig(String serverHost, int serverPort) {
    private static final String DEFAULT_SERVER_HOST = "127.0.0.1";
    private static final int DEFAULT_SERVER_PORT = 5555;

    public ClientConfig {
        Objects.requireNonNull(serverHost, "serverHost");
        if (serverHost.isBlank()) {
            throw new IllegalArgumentException("serverHost must not be blank");
        }
        if (serverPort <= 0 || serverPort > 65_535) {
            throw new IllegalArgumentException("serverPort must be between 1 and 65535");
        }
    }

    public static ClientConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    static ClientConfig fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        String host = valueOrDefault(environment.get("SERVER_HOST"), DEFAULT_SERVER_HOST);
        String rawPort = valueOrDefault(environment.get("SERVER_PORT"), Integer.toString(DEFAULT_SERVER_PORT));
        try {
            return new ClientConfig(host, Integer.parseInt(rawPort));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("SERVER_PORT must be a valid integer", exception);
        }
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
