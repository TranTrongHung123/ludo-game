package vn.ptit.ltm.server.room;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.server.repository.UserAccountRecord;
import vn.ptit.ltm.server.session.PlayerSession;
import vn.ptit.ltm.server.session.SessionManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class RoomManagerTest {
    @Test
    void generatesShortReadableCodesAndAcceptsLowercaseInput() {
        try (var sessions = new SessionManager(Duration.ofSeconds(2))) {
            var manager = new RoomManager();
            var codes = new HashSet<String>();
            for (long id = 1; id <= 256; id++) {
                var room = manager.create(player(sessions, id));
                assertTrue(room.roomId().matches("[A-HJ-NP-Z2-9]{6}"));
                assertTrue(codes.add(room.roomId()));
            }
            var room = manager.snapshot().getFirst();
            var guest = player(sessions, 1000);
            assertSame(room, manager.join(" " + room.roomId().toLowerCase(java.util.Locale.ROOT) + " ", guest));
            assertSame(room, manager.requireRoomForPlayer(room.roomId(), guest.user().id()));
            assertTrue(manager.leaveCurrent(guest.user().id()).isPresent());
        }
    }

    @Test
    void retriesCollisionWithoutOverwritingExistingRoom() {
        var generated = new AtomicInteger();
        var manager = new RoomManager(() -> generated.getAndIncrement() < 2 ? "ABC234" : "XYZ789");
        try (var sessions = new SessionManager(Duration.ofSeconds(2))) {
            var first = manager.create(player(sessions, 1));
            var second = manager.create(player(sessions, 2));
            assertEquals("ABC234", first.roomId());
            assertEquals("XYZ789", second.roomId());
            assertSame(first, manager.findById("ABC234").orElseThrow());
            assertSame(second, manager.findByPlayer(2).orElseThrow());
        }
    }

    @Test
    void repeatedCollisionsFailWithoutLeakingMembership() {
        var manager = new RoomManager(() -> "ABC234");
        try (var sessions = new SessionManager(Duration.ofSeconds(2))) {
            var first = manager.create(player(sessions, 1));
            var exception = assertThrows(RoomException.class, () -> manager.create(player(sessions, 2)));
            assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, exception.errorCode());
            assertEquals(List.of(first), manager.snapshot());
            assertTrue(manager.findByPlayer(2).isEmpty());
        }
    }

    @Test
    void concurrentCreationReservesUniqueCodesAtomically() throws Exception {
        var sequence = new AtomicInteger();
        var manager = new RoomManager(() -> {
            int value = sequence.getAndIncrement();
            return value < 16 ? "ABC234" : "R" + String.format(java.util.Locale.ROOT, "%05d", value);
        });
        try (var sessions = new SessionManager(Duration.ofSeconds(2));
             var executor = Executors.newFixedThreadPool(8)) {
            var players = LongStream.rangeClosed(1, 64).mapToObj(id -> player(sessions, id)).toList();
            List<Callable<GameRoom>> tasks = players.stream()
                    .map(p -> (Callable<GameRoom>) () -> manager.create(p)).toList();
            var codes = new HashSet<String>();
            for (var result : executor.invokeAll(tasks)) {
                var room = result.get();
                assertTrue(codes.add(room.roomId()));
                assertSame(room, manager.findById(room.roomId()).orElseThrow());
            }
            assertEquals(64, manager.snapshot().size());
            for (var player : players) assertTrue(manager.findByPlayer(player.user().id()).isPresent());
        }
    }

    private static PlayerSession player(SessionManager sessions, long id) {
        var now = Instant.now();
        return sessions.createSession(new UserAccountRecord(id, "user" + id, "hash", "Player " + id,
                BigDecimal.ZERO, 0, now, now), "connection-" + id);
    }
}
