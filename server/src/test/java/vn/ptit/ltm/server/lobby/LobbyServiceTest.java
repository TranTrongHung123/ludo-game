package vn.ptit.ltm.server.lobby;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.enums.PlayerPresenceState;
import vn.ptit.ltm.server.network.ConnectionRegistry;
import vn.ptit.ltm.server.repository.UserAccountRecord;
import vn.ptit.ltm.server.session.PlayerSession;
import vn.ptit.ltm.server.session.SessionManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LobbyServiceTest {
    @Test
    void exposesPublicPlayerDataAndServerControlledPresence() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(1))) {
            PlayerSession first = sessions.createSession(user(2, "Beta", "4.5", 1), "connection-2");
            sessions.createSession(user(1, "Alpha", "8.0", 3), "connection-1");
            LobbyService lobby = new LobbyService(sessions, new ConnectionRegistry());

            var initial = lobby.onlinePlayers();
            assertEquals(2, initial.players().size());
            assertEquals("1", initial.players().getFirst().playerId());
            assertEquals("Alpha", initial.players().getFirst().displayName());
            assertEquals(new BigDecimal("8.0"), initial.players().getFirst().totalScore());
            assertEquals(3, initial.players().getFirst().firstPlaceCount());
            assertEquals(PlayerPresenceState.IDLE, initial.players().getFirst().presenceState());

            sessions.updatePresence(first.sessionId(), PlayerPresenceState.IN_ROOM);
            assertEquals(
                    PlayerPresenceState.IN_ROOM,
                    lobby.onlinePlayers().players().get(1).presenceState()
            );
        }
    }

    private static UserAccountRecord user(
            long id,
            String displayName,
            String score,
            int firstPlaceCount
    ) {
        Instant now = Instant.now();
        return new UserAccountRecord(
                id,
                "user" + id,
                "hash",
                displayName,
                new BigDecimal(score),
                firstPlaceCount,
                now,
                now
        );
    }
}
