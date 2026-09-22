package vn.ptit.ltm.server.room;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.enums.*;
import vn.ptit.ltm.common.protocol.JsonMessageCodec;
import vn.ptit.ltm.common.dto.session.ReconnectResult;
import vn.ptit.ltm.server.network.ConnectionRegistry;
import vn.ptit.ltm.server.repository.*;
import vn.ptit.ltm.server.service.MatchService;
import vn.ptit.ltm.server.service.ServiceException;
import vn.ptit.ltm.server.session.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RematchIntegrationTest {
    private static PlayerSession player(SessionManager sessions, long id) {
        return sessions.createSession(new UserAccountRecord(id, "user" + id, "hash", "Player " + id,
                BigDecimal.ZERO, 0, Instant.now(), Instant.now()), "connection-" + id);
    }

    private static String start(RoomService rooms, PlayerSession a, PlayerSession b) {
        String id = rooms.createRoom(a.sessionId(), a.connectionId()).roomId();
        rooms.joinRoom(b.sessionId(), b.connectionId(), id);
        rooms.setReady(a.sessionId(), a.connectionId(), id, true);
        rooms.setReady(b.sessionId(), b.connectionId(), id, true);
        rooms.startGame(a.sessionId(), a.connectionId(), id);
        return id;
    }

    @Test
    void reconnectRestoresMissedResultAndReadyReopensSameRoomWithFreshMatch() throws Exception {
        try (SessionManager sessions = new SessionManager(Duration.ofMinutes(1));
             RoomService rooms = new RoomService(sessions, new ConnectionRegistry())) {
            var a = player(sessions, 1);
            var b = player(sessions, 2);
            String id = start(rooms, a, b);
            String oldMatch = rooms.gameForPlayer(1).orElseThrow().matchId();
            sessions.disconnect(a.connectionId());
            rooms.leaveRoom(b.sessionId(), b.connectionId(), id);
            var reconnected = sessions.reconnect(a.sessionId(), "reconnected");
            var restored = rooms.restoreSession(reconnected);
            assertEquals(RoomState.FINISHED, restored.gameState().roomState());
            assertEquals(oldMatch, restored.gameOver().matchId());
            assertEquals(0, new BigDecimal("3").compareTo(restored.gameOver().standings().getFirst().scoreEarned()));
            var mapper = new JsonMessageCodec().objectMapper();
            var roundTrip = mapper.readValue(mapper.writeValueAsBytes(restored), ReconnectResult.class);
            assertEquals(restored, roundTrip);

            var waiting = rooms.setReady(a.sessionId(), a.connectionId(), id, true);
            assertEquals(RoomState.WAITING, waiting.state());
            assertEquals(1, waiting.players().size());
            assertTrue(waiting.players().getFirst().ready());
            assertEquals(PlayerPresenceState.IN_ROOM, a.presenceState());
            assertTrue(rooms.gameForPlayer(1).isEmpty());
            assertNull(rooms.restoreSession(a).gameOver());
            // Departed slot is reusable and cannot carry readiness into the new match.
            var joined = rooms.joinRoom(b.sessionId(), b.connectionId(), id);
            assertFalse(joined.players().stream().filter(p -> p.playerId().equals("2")).findFirst().orElseThrow().ready());
            assertThrows(RoomException.class, () -> rooms.startGame(a.sessionId(), a.connectionId(), id));
            rooms.setReady(b.sessionId(), b.connectionId(), id, true);
            var next = rooms.startGame(a.sessionId(), a.connectionId(), id);
            assertNotEquals(oldMatch, next.matchId());
            assertEquals("1", next.currentPlayerId());
            assertEquals(TurnState.WAITING_FOR_ROLL, next.turnState());
            assertEquals(20, next.specialCells().size());
            assertTrue(next.participants().stream().flatMap(p -> p.pieces().stream()).allMatch(p ->
                    p.state() == PieceState.IN_YARD && p.stepCount() == -1 && !p.slowed() && !p.shielded()));
        }
    }

    @Test
    void rematchRetriesPersistenceBeforeResetAndDoesNotLoseFinishedStateOnFailure() throws Exception {
        try (SessionManager sessions = new SessionManager(Duration.ofMinutes(1))) {
            var repository = mock(MatchRepository.class);
            when(repository.saveCompletedMatch(any())).thenThrow(new SQLException("offline"));
            try (RoomService rooms = new RoomService(sessions, new ConnectionRegistry(), new MatchService(repository, sessions))) {
                var a = player(sessions, 1);
                var b = player(sessions, 2);
                String id = start(rooms, a, b);
                assertThrows(ServiceException.class, () -> rooms.leaveRoom(b.sessionId(), b.connectionId(), id));
                String match = rooms.gameForPlayer(1).orElseThrow().matchId();
                assertThrows(ServiceException.class, () -> rooms.setReady(a.sessionId(), a.connectionId(), id, true));
                assertEquals(match, rooms.gameForPlayer(1).orElseThrow().matchId());
                assertEquals(RoomState.FINISHED, rooms.roomForPlayer(1).orElseThrow().state());

                doAnswer(invocation -> {
                    CompletedMatchRecord record = invocation.getArgument(0);
                    return new PersistedMatchResult(record.matchId(), record.players().stream().map(p ->
                            new PersistedPlayerResult(p.userId(), p.displayName(), p.color(), p.rank(), p.scoreEarned(),
                                    p.scoreEarned(), p.rank() == 1 ? 1 : 0, p.status())).toList());
                }).when(repository).saveCompletedMatch(any());
                rooms.setReady(a.sessionId(), a.connectionId(), id, true);
                assertEquals(RoomState.WAITING, rooms.roomForPlayer(1).orElseThrow().state());
                assertEquals(0, new BigDecimal("3").compareTo(a.user().score()));
                rooms.setReady(a.sessionId(), a.connectionId(), id, true);
                verify(repository, times(3)).saveCompletedMatch(any());
            }
        }
    }

    @Test
    void finishedRoomKeepsLiveSlotsButClearsEveryReadyFlagAndOldTimeout() {
        try (SessionManager sessions = new SessionManager(Duration.ofMinutes(1))) {
            var a = player(sessions, 1);
            var b = player(sessions, 2);
            GameRoom room = new GameRoom("same-room", a);
            room.add(b);
            room.setReady(1, true); room.setReady(2, true);
            var presence = Map.of(1L, PlayerPresenceState.IN_ROOM, 2L, PlayerPresenceState.IN_ROOM);
            var initial = room.start(1, presence, Instant.now());
            var oldTimeout = room.timeoutExpectation().orElseThrow();
            room.forfeitActivePlayer(2, Instant.now(), presence);
            assertThrows(RoomException.class, () -> room.reopenForRematch(99));
            assertTrue(room.reopenForRematch(1));
            assertTrue(room.snapshot(presence).players().stream().noneMatch(p -> p.ready()));
            room.setReady(1, true); room.setReady(2, true);
            var next = room.start(1, presence, Instant.now());
            assertNotEquals(initial.matchId(), next.matchId());
            assertTrue(room.expireIfExpected(oldTimeout, Instant.now().plusSeconds(60), presence).isEmpty());
            assertEquals(next.matchId(), room.gameSnapshot(presence).matchId());
        }
    }
}
