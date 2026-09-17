package vn.ptit.ltm.client.state;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.dto.auth.LoginResult;
import vn.ptit.ltm.common.dto.lobby.OnlinePlayersPayload;
import vn.ptit.ltm.common.dto.player.PlayerProfileDto;
import vn.ptit.ltm.common.dto.player.PlayerSummaryDto;
import vn.ptit.ltm.common.enums.PlayerPresenceState;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientSessionStateTest {
    @Test
    void storesAndClearsAuthenticatedSessionWithoutExposingItThroughProfile() {
        ClientSessionState state = new ClientSessionState();
        assertFalse(state.isAuthenticated());
        assertThrows(IllegalStateException.class, state::requireSessionId);

        state.authenticate(new LoginResult("secret-session", profile("alice")));

        assertTrue(state.isAuthenticated());
        assertEquals("secret-session", state.requireSessionId());
        assertEquals("alice", state.profile().orElseThrow().username());

        state.clear();
        assertFalse(state.isAuthenticated());
        assertTrue(state.profile().isEmpty());
    }

    @Test
    void refreshesOwnScoreFromAuthoritativeLobbySnapshot() {
        ClientSessionState state = new ClientSessionState();
        state.authenticate(new LoginResult("secret-session", profile("alice")));

        state.updateOnlinePlayers(new OnlinePlayersPayload(List.of(new PlayerSummaryDto(
                "1",
                "Alice",
                new BigDecimal("4.5"),
                1,
                PlayerPresenceState.IDLE
        ))));

        assertEquals(new BigDecimal("4.5"), state.profile().orElseThrow().totalScore());
        assertEquals(1, state.profile().orElseThrow().firstPlaceCount());
    }

    private static PlayerProfileDto profile(String username) {
        return new PlayerProfileDto(
                "1",
                username,
                "Alice",
                BigDecimal.ZERO,
                0,
                0,
                0,
                0
        );
    }
}
