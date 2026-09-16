package vn.ptit.ltm.client.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientConfigTest {
    @Test
    void usesDefaultsAndSupportsEnvironmentOverrides() {
        assertEquals(new ClientConfig("127.0.0.1", 5555), ClientConfig.fromEnvironment(Map.of()));
        assertEquals(
                new ClientConfig("game.example.test", 6000),
                ClientConfig.fromEnvironment(Map.of(
                        "SERVER_HOST", "game.example.test",
                        "SERVER_PORT", "6000"
                ))
        );
    }

    @Test
    void rejectsInvalidPort() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ClientConfig.fromEnvironment(Map.of("SERVER_PORT", "not-a-port"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ClientConfig.fromEnvironment(Map.of("SERVER_PORT", "70000"))
        );
    }
}
