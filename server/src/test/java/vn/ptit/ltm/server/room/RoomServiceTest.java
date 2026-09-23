package vn.ptit.ltm.server.room;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.enums.PieceColor;
import vn.ptit.ltm.common.enums.MatchParticipantStatus;
import vn.ptit.ltm.common.enums.PlayerPresenceState;
import vn.ptit.ltm.common.enums.TurnState;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.server.network.ConnectionRegistry;
import vn.ptit.ltm.server.repository.UserAccountRecord;
import vn.ptit.ltm.server.session.PlayerSession;
import vn.ptit.ltm.server.session.SessionManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomServiceTest {
    @Test
    void createJoinLeaveReuseSlotAndTransferHostByJoinOrder() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(2))) {
            PlayerSession host = session(sessions, 1);
            PlayerSession second = session(sessions, 2);
            PlayerSession third = session(sessions, 3);
            PlayerSession fourth = session(sessions, 4);
            RoomService rooms = new RoomService(sessions, new ConnectionRegistry());

            var created = rooms.createRoom(host.sessionId(), host.connectionId());
            assertEquals("1", created.hostPlayerId());
            assertEquals(PieceColor.RED, created.players().getFirst().color());
            RoomException duplicate = assertThrows(
                    RoomException.class,
                    () -> rooms.createRoom(host.sessionId(), host.connectionId())
            );
            assertEquals(ErrorCode.ALREADY_IN_ROOM, duplicate.errorCode());

            rooms.joinRoom(second.sessionId(), second.connectionId(), created.roomId());
            rooms.joinRoom(third.sessionId(), third.connectionId(), created.roomId());
            rooms.leaveRoom(second.sessionId(), second.connectionId(), created.roomId());
            var afterReuse = rooms.joinRoom(fourth.sessionId(), fourth.connectionId(), created.roomId());

            assertEquals(1, player(afterReuse.players(), "4").slotIndex());
            assertEquals(PieceColor.BLUE, player(afterReuse.players(), "4").color());
            assertEquals(2, player(afterReuse.players(), "3").slotIndex());

            rooms.leaveRoom(host.sessionId(), host.connectionId(), created.roomId());
            var transferred = rooms.roomForPlayer(third.user().id()).orElseThrow();
            assertEquals("3", transferred.hostPlayerId());
            assertNotEquals("4", transferred.hostPlayerId());
            assertEquals(PlayerPresenceState.IDLE, host.presenceState());
        }
    }

    @Test
    void rejectsFifthPlayerAndKeepsFailedJoinerIdle() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(2))) {
            List<PlayerSession> players = java.util.stream.LongStream.rangeClosed(1, 5)
                    .mapToObj(id -> session(sessions, id))
                    .toList();
            RoomService rooms = new RoomService(sessions, new ConnectionRegistry());
            var room = rooms.createRoom(players.getFirst().sessionId(), players.getFirst().connectionId());
            for (int index = 1; index < 4; index++) {
                PlayerSession player = players.get(index);
                rooms.joinRoom(player.sessionId(), player.connectionId(), room.roomId());
            }

            PlayerSession fifth = players.get(4);
            RoomException failure = assertThrows(
                    RoomException.class,
                    () -> rooms.joinRoom(fifth.sessionId(), fifth.connectionId(), room.roomId())
            );
            assertEquals(ErrorCode.ROOM_FULL, failure.errorCode());
            assertEquals(PlayerPresenceState.IDLE, fifth.presenceState());
            assertEquals(4, rooms.roomForPlayer(players.getFirst().user().id()).orElseThrow().players().size());
            assertEquals(
                    "5",
                    rooms.createRoom(fifth.sessionId(), fifth.connectionId()).hostPlayerId()
            );
        }
    }

    @Test
    void serializesConcurrentJoinsForTheLastSlot() throws Exception {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(2));
             ExecutorService executor = Executors.newFixedThreadPool(2)) {
            PlayerSession host = session(sessions, 1);
            PlayerSession second = session(sessions, 2);
            PlayerSession third = session(sessions, 3);
            PlayerSession candidateA = session(sessions, 4);
            PlayerSession candidateB = session(sessions, 5);
            RoomService rooms = new RoomService(sessions, new ConnectionRegistry());
            var room = rooms.createRoom(host.sessionId(), host.connectionId());
            rooms.joinRoom(second.sessionId(), second.connectionId(), room.roomId());
            rooms.joinRoom(third.sessionId(), third.connectionId(), room.roomId());
            CountDownLatch start = new CountDownLatch(1);

            Callable<Boolean> joinA = () -> joinAfterSignal(rooms, candidateA, room.roomId(), start);
            Callable<Boolean> joinB = () -> joinAfterSignal(rooms, candidateB, room.roomId(), start);
            Future<Boolean> first = executor.submit(joinA);
            Future<Boolean> secondResult = executor.submit(joinB);
            start.countDown();

            int successes = (first.get() ? 1 : 0) + (secondResult.get() ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(4, rooms.roomForPlayer(host.user().id()).orElseThrow().players().size());
            assertTrue(candidateA.presenceState() == PlayerPresenceState.IN_ROOM
                    ^ candidateB.presenceState() == PlayerPresenceState.IN_ROOM);
        }
    }

    @Test
    void disconnectKeepsMembershipAndReconnectRestoresRoomSnapshot() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(2))) {
            PlayerSession host = session(sessions, 1);
            RoomService rooms = new RoomService(sessions, new ConnectionRegistry());
            var room = rooms.createRoom(host.sessionId(), host.connectionId());

            sessions.disconnect(host.connectionId());
            rooms.onSessionsChanged();
            var disconnected = rooms.roomForPlayer(host.user().id()).orElseThrow();
            assertEquals(PlayerPresenceState.DISCONNECTED, disconnected.players().getFirst().presenceState());

            PlayerSession reconnected = sessions.reconnect(host.sessionId(), "connection-reconnected");
            rooms.onSessionsChanged();
            assertEquals(PlayerPresenceState.IN_ROOM, reconnected.presenceState());
            assertEquals(room.roomId(), rooms.roomForPlayer(host.user().id()).orElseThrow().roomId());
        }
    }

    @Test
    void deletingLastMemberRemovesTheRoom() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(2))) {
            PlayerSession host = session(sessions, 1);
            RoomService rooms = new RoomService(sessions, new ConnectionRegistry());
            var room = rooms.createRoom(host.sessionId(), host.connectionId());

            rooms.leaveRoom(host.sessionId(), host.connectionId(), room.roomId());

            assertFalse(rooms.roomForPlayer(host.user().id()).isPresent());
            RoomException failure = assertThrows(
                    RoomException.class,
                    () -> rooms.joinRoom(host.sessionId(), host.connectionId(), room.roomId())
            );
            assertEquals(ErrorCode.ROOM_NOT_FOUND, failure.errorCode());
        }
    }

    @Test
    void gracePeriodExpirationRemovesDisconnectedPlayerFromRoom() throws Exception {
        try (SessionManager sessions = new SessionManager(Duration.ofMillis(60))) {
            PlayerSession host = session(sessions, 1);
            PlayerSession second = session(sessions, 2);
            RoomService rooms = new RoomService(sessions, new ConnectionRegistry());
            sessions.addEventListener(rooms::onSessionsChanged);
            var room = rooms.createRoom(host.sessionId(), host.connectionId());
            rooms.joinRoom(second.sessionId(), second.connectionId(), room.roomId());

            sessions.disconnect(second.connectionId());
            assertTrue(rooms.roomForPlayer(second.user().id()).isPresent());
            await(() -> rooms.roomForPlayer(second.user().id()).isEmpty());

            var remaining = rooms.roomForPlayer(host.user().id()).orElseThrow();
            assertEquals(1, remaining.players().size());
            assertEquals("1", remaining.hostPlayerId());
        }
    }

    @Test
    void invitationCanBeAcceptedOnceAndOnlyByTheTarget() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(2))) {
            PlayerSession host = session(sessions, 1);
            PlayerSession target = session(sessions, 2);
            PlayerSession other = session(sessions, 3);
            RoomService rooms = new RoomService(sessions, new ConnectionRegistry());
            var room = rooms.createRoom(host.sessionId(), host.connectionId());
            rooms.joinRoom(other.sessionId(), other.connectionId(), room.roomId());

            RoomException nonHostInvite = assertThrows(
                    RoomException.class,
                    () -> rooms.invitePlayer(
                            other.sessionId(),
                            other.connectionId(),
                            room.roomId(),
                            Long.toString(target.user().id())
                    )
            );
            assertEquals(ErrorCode.NOT_ROOM_HOST, nonHostInvite.errorCode());

            var replacedInvitation = rooms.invitePlayer(
                    host.sessionId(),
                    host.connectionId(),
                    room.roomId(),
                    Long.toString(target.user().id())
            );
            var invitation = rooms.invitePlayer(
                    host.sessionId(),
                    host.connectionId(),
                    room.roomId(),
                    Long.toString(target.user().id())
            );
            assertNotEquals(replacedInvitation.invitationId(), invitation.invitationId());
            RoomException replaced = assertThrows(
                    RoomException.class,
                    () -> rooms.rejectInvitation(
                            target.sessionId(),
                            target.connectionId(),
                            replacedInvitation.invitationId()
                    )
            );
            assertEquals(ErrorCode.INVITATION_NOT_FOUND, replaced.errorCode());

            RoomException wrongTarget = assertThrows(
                    RoomException.class,
                    () -> rooms.acceptInvitation(
                            other.sessionId(),
                            other.connectionId(),
                            invitation.invitationId()
                    )
            );
            assertEquals(ErrorCode.INVITATION_NOT_FOUND, wrongTarget.errorCode());

            var joined = rooms.acceptInvitation(
                    target.sessionId(),
                    target.connectionId(),
                    invitation.invitationId()
            );
            assertEquals(3, joined.players().size());
            RoomException reused = assertThrows(
                    RoomException.class,
                    () -> rooms.rejectInvitation(
                            target.sessionId(),
                            target.connectionId(),
                            invitation.invitationId()
                    )
            );
            assertEquals(ErrorCode.INVITATION_NOT_FOUND, reused.errorCode());
        }
    }

    @Test
    void invitationExpiresAfterCanonicalTtl() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(2))) {
            PlayerSession host = session(sessions, 1);
            PlayerSession target = session(sessions, 2);
            MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
            RoomService rooms = new RoomService(
                    sessions,
                    new ConnectionRegistry(),
                    clock,
                    Duration.ofSeconds(60)
            );
            var room = rooms.createRoom(host.sessionId(), host.connectionId());
            var invitation = rooms.invitePlayer(
                    host.sessionId(),
                    host.connectionId(),
                    room.roomId(),
                    Long.toString(target.user().id())
            );

            clock.advance(Duration.ofSeconds(60));
            RoomException expired = assertThrows(
                    RoomException.class,
                    () -> rooms.acceptInvitation(
                            target.sessionId(),
                            target.connectionId(),
                            invitation.invitationId()
                    )
            );
            assertEquals(ErrorCode.INVITATION_EXPIRED, expired.errorCode());
            assertEquals(PlayerPresenceState.IDLE, target.presenceState());
        }
    }

    @Test
    void membershipChangesInvalidatePendingInvitations() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(2))) {
            PlayerSession host = session(sessions, 1);
            PlayerSession target = session(sessions, 2);
            PlayerSession joiner = session(sessions, 3);
            RoomService rooms = new RoomService(sessions, new ConnectionRegistry());
            var room = rooms.createRoom(host.sessionId(), host.connectionId());
            var invalidatedByJoin = rooms.invitePlayer(
                    host.sessionId(),
                    host.connectionId(),
                    room.roomId(),
                    Long.toString(target.user().id())
            );

            rooms.joinRoom(joiner.sessionId(), joiner.connectionId(), room.roomId());
            RoomException afterJoin = assertThrows(
                    RoomException.class,
                    () -> rooms.acceptInvitation(
                            target.sessionId(),
                            target.connectionId(),
                            invalidatedByJoin.invitationId()
                    )
            );
            assertEquals(ErrorCode.INVITATION_NOT_FOUND, afterJoin.errorCode());

            var invalidatedByCreate = rooms.invitePlayer(
                    host.sessionId(),
                    host.connectionId(),
                    room.roomId(),
                    Long.toString(target.user().id())
            );
            rooms.createRoom(target.sessionId(), target.connectionId());
            RoomException afterCreate = assertThrows(
                    RoomException.class,
                    () -> rooms.rejectInvitation(
                            target.sessionId(),
                            target.connectionId(),
                            invalidatedByCreate.invitationId()
                    )
            );
            assertEquals(ErrorCode.INVITATION_NOT_FOUND, afterCreate.errorCode());
        }
    }

    @Test
    void allPlayersMustBeReadyAndOnlyHostCanStartCanonicalGameState() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(2))) {
            PlayerSession host = session(sessions, 1);
            PlayerSession second = session(sessions, 2);
            MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
            RoomService rooms = new RoomService(
                    sessions,
                    new ConnectionRegistry(),
                    clock,
                    Duration.ofSeconds(60)
            );
            var room = rooms.createRoom(host.sessionId(), host.connectionId());

            rooms.setReady(host.sessionId(), host.connectionId(), room.roomId(), true);
            RoomException notEnough = assertThrows(
                    RoomException.class,
                    () -> rooms.startGame(host.sessionId(), host.connectionId(), room.roomId())
            );
            assertEquals(ErrorCode.NOT_ENOUGH_PLAYERS, notEnough.errorCode());

            rooms.joinRoom(second.sessionId(), second.connectionId(), room.roomId());

            RoomException nonHost = assertThrows(
                    RoomException.class,
                    () -> rooms.startGame(second.sessionId(), second.connectionId(), room.roomId())
            );
            assertEquals(ErrorCode.NOT_ROOM_HOST, nonHost.errorCode());
            RoomException notReady = assertThrows(
                    RoomException.class,
                    () -> rooms.startGame(host.sessionId(), host.connectionId(), room.roomId())
            );
            assertEquals(ErrorCode.PLAYER_NOT_READY, notReady.errorCode());

            rooms.setReady(second.sessionId(), second.connectionId(), room.roomId(), true);
            var unreadyRoom = rooms.setReady(
                    second.sessionId(),
                    second.connectionId(),
                    room.roomId(),
                    false
            );
            assertFalse(player(unreadyRoom.players(), "2").ready());
            RoomException unready = assertThrows(
                    RoomException.class,
                    () -> rooms.startGame(host.sessionId(), host.connectionId(), room.roomId())
            );
            assertEquals(ErrorCode.PLAYER_NOT_READY, unready.errorCode());

            var readyRoom = rooms.setReady(
                    second.sessionId(),
                    second.connectionId(),
                    room.roomId(),
                    true
            );
            assertTrue(readyRoom.players().stream().allMatch(player -> player.ready()));

            var game = rooms.startGame(host.sessionId(), host.connectionId(), room.roomId());
            assertEquals(vn.ptit.ltm.common.enums.RoomState.PLAYING, game.roomState());
            assertEquals("1", game.currentPlayerId());
            assertEquals(0, game.currentSlot());
            assertEquals(vn.ptit.ltm.common.enums.TurnState.WAITING_FOR_ROLL, game.turnState());
            assertEquals(120_000L, game.phaseDurationMillis());
            assertEquals(clock.instant().toEpochMilli() + 120_000L, game.serverDeadlineEpochMillis());
            assertEquals(2, game.participants().size());
            assertEquals(16, game.specialCells().size());
            assertTrue(game.participants().stream().allMatch(participant ->
                    participant.pieces().size() == 4
                            && participant.pieces().stream().allMatch(piece -> piece.stepCount() == -1)
                            && participant.presenceState() == PlayerPresenceState.PLAYING
            ));
            assertEquals(PlayerPresenceState.PLAYING, host.presenceState());
            assertEquals(PlayerPresenceState.PLAYING, second.presenceState());
            assertTrue(rooms.gameForPlayer(second.user().id()).isPresent());

        }
    }

    @Test
    void quitDuringPlayingForfeitsImmediatelyAndReleasesMembership() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(2))) {
            PlayerSession host = session(sessions, 1);
            PlayerSession quitter = session(sessions, 2);
            PlayerSession third = session(sessions, 3);
            try (RoomService rooms = new RoomService(
                    sessions,
                    new ConnectionRegistry(),
                    () -> 3
            )) {
                String roomId = startThreePlayerGame(rooms, host, quitter, third);

                rooms.leaveRoom(quitter.sessionId(), quitter.connectionId(), roomId);

                assertEquals(PlayerPresenceState.IDLE, quitter.presenceState());
                assertTrue(rooms.roomForPlayer(quitter.user().id()).isEmpty());
                var continued = rooms.gameForPlayer(host.user().id()).orElseThrow();
                var forfeited = continued.participants().stream()
                        .filter(participant -> participant.playerId().equals("2"))
                        .findFirst()
                        .orElseThrow();
                assertEquals(vn.ptit.ltm.common.enums.RoomState.PLAYING, continued.roomState());
                assertEquals("1", continued.currentPlayerId());
                assertEquals(MatchParticipantStatus.FORFEITED, forfeited.matchStatus());
                assertEquals(3, forfeited.rank());
                assertEquals(BigDecimal.ZERO, forfeited.scoreEarned());
                assertTrue(forfeited.pieces().stream().allMatch(piece -> piece.stepCount() == -1));
                assertEquals(2, rooms.roomForPlayer(host.user().id()).orElseThrow().players().size());

                // Mapping phòng cũ đã được giải phóng nên người Quit có thể tạo phòng mới ngay.
                assertEquals(
                        quitter.user().id(),
                        Long.parseLong(rooms.createRoom(quitter.sessionId(), quitter.connectionId()).hostPlayerId())
                );
            }
        }
    }

    @Test
    void hostQuitFinishesTwoPlayerGameAndWinnerCanLeaveFinishedRoom() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(2))) {
            PlayerSession host = session(sessions, 1);
            PlayerSession second = session(sessions, 2);
            try (RoomService rooms = new RoomService(
                    sessions,
                    new ConnectionRegistry(),
                    () -> 3
            )) {
                String roomId = startTwoPlayerGame(rooms, host, second);

                rooms.leaveRoom(host.sessionId(), host.connectionId(), roomId);

                assertEquals(PlayerPresenceState.IDLE, host.presenceState());
                assertTrue(rooms.roomForPlayer(host.user().id()).isEmpty());
                var remainingRoom = rooms.roomForPlayer(second.user().id()).orElseThrow();
                assertEquals(vn.ptit.ltm.common.enums.RoomState.FINISHED, remainingRoom.state());
                assertEquals("2", remainingRoom.hostPlayerId());
                assertEquals(1, remainingRoom.players().size());
                var finished = rooms.gameForPlayer(second.user().id()).orElseThrow();
                assertEquals(MatchParticipantStatus.FORFEITED, finished.participants().getFirst().matchStatus());
                assertEquals(MatchParticipantStatus.COMPLETED, finished.participants().get(1).matchStatus());

                rooms.leaveRoom(second.sessionId(), second.connectionId(), roomId);

                assertEquals(PlayerPresenceState.IDLE, second.presenceState());
                assertTrue(rooms.roomForPlayer(second.user().id()).isEmpty());
                assertTrue(rooms.gameForPlayer(second.user().id()).isEmpty());
            }
        }
    }

    @Test
    void rollTimeoutAdvancesExactlyOnceAtTwoMinuteDeadline() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(2))) {
            PlayerSession host = session(sessions, 1);
            PlayerSession second = session(sessions, 2);
            MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
            try (RoomService rooms = new RoomService(
                    sessions,
                    new ConnectionRegistry(),
                    clock,
                    Duration.ofSeconds(60),
                    () -> 6
            )) {
                String roomId = startTwoPlayerGame(rooms, host, second);

                sessions.disconnect(host.connectionId());
                clock.advance(Duration.ofMillis(119_999));
                rooms.processExpiredTurns();
                assertEquals("1", rooms.gameForPlayer(host.user().id()).orElseThrow().currentPlayerId());
                clock.advance(Duration.ofMillis(1));
                rooms.processExpiredTurns();
                var afterTimeout = rooms.gameForPlayer(host.user().id()).orElseThrow();
                assertEquals("2", afterTimeout.currentPlayerId());
                assertEquals(TurnState.WAITING_FOR_ROLL, afterTimeout.turnState());
                assertEquals(1L, afterTimeout.stateVersion());
                assertEquals(clock.millis() + 120_000L, afterTimeout.serverDeadlineEpochMillis());
                var disconnected = afterTimeout.participants().stream()
                        .filter(participant -> participant.playerId().equals("1"))
                        .findFirst()
                        .orElseThrow();
                assertEquals(PlayerPresenceState.DISCONNECTED, disconnected.presenceState());
                assertEquals(MatchParticipantStatus.ACTIVE, disconnected.matchStatus());

                rooms.processExpiredTurns();
                var afterDuplicateCallback = rooms.gameForPlayer(host.user().id()).orElseThrow();
                assertEquals("2", afterDuplicateCallback.currentPlayerId());
                assertEquals(1L, afterDuplicateCallback.stateVersion());
                assertEquals(roomId, afterDuplicateCallback.roomId());
            }
        }
    }

    @Test
    void reconnectDuringMovePhaseRestoresSameAuthoritativeDeadlineAndCanContinueTurn() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(2))) {
            PlayerSession host = session(sessions, 1);
            PlayerSession second = session(sessions, 2);
            MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
            try (RoomService rooms = new RoomService(
                    sessions,
                    new ConnectionRegistry(),
                    clock,
                    Duration.ofSeconds(60),
                    () -> 6
            )) {
                String roomId = startTwoPlayerGame(rooms, host, second);
                rooms.rollDice(host.sessionId(), host.connectionId(), roomId);
                var beforeDisconnect = rooms.gameForPlayer(host.user().id()).orElseThrow();

                sessions.disconnect(host.connectionId());
                rooms.onSessionsChanged();
                assertEquals(
                        PlayerPresenceState.DISCONNECTED,
                        rooms.gameForPlayer(host.user().id()).orElseThrow().participants().getFirst().presenceState()
                );

                PlayerSession restored = sessions.reconnect(host.sessionId(), "connection-restored");
                rooms.onSessionsChanged();
                var afterReconnect = rooms.gameForPlayer(host.user().id()).orElseThrow();
                assertEquals(PlayerPresenceState.PLAYING, restored.presenceState());
                assertEquals(TurnState.WAITING_FOR_MOVE, afterReconnect.turnState());
                assertEquals(beforeDisconnect.stateVersion(), afterReconnect.stateVersion());
                assertEquals(
                        beforeDisconnect.serverDeadlineEpochMillis(),
                        afterReconnect.serverDeadlineEpochMillis()
                );
                assertEquals(beforeDisconnect.validPieceIds(), afterReconnect.validPieceIds());

                var moved = rooms.movePiece(
                        host.sessionId(),
                        restored.connectionId(),
                        roomId,
                        afterReconnect.validPieceIds().getFirst()
                );
                assertEquals(0, moved.piece().stepCount());
                assertEquals(-1, moved.gameState().lastMove().fromStep());
                assertEquals(0, moved.gameState().lastMove().landedStep());
                assertEquals(moved.gameState().lastMove(), rooms.gameForPlayer(second.user().id()).orElseThrow().lastMove());
            }
        }
    }

    @Test
    void gracePeriodExpirationForfeitsPlayerAndFinishesTwoPlayerGame() throws Exception {
        try (SessionManager sessions = new SessionManager(Duration.ofMillis(80))) {
            PlayerSession host = session(sessions, 1);
            PlayerSession second = session(sessions, 2);
            try (RoomService rooms = new RoomService(
                    sessions,
                    new ConnectionRegistry(),
                    () -> 3
            )) {
                sessions.addEventListener(rooms::onSessionsChanged);
                String roomId = startTwoPlayerGame(rooms, host, second);

                sessions.disconnect(second.connectionId());
                assertEquals(
                        MatchParticipantStatus.ACTIVE,
                        rooms.gameForPlayer(host.user().id()).orElseThrow().participants().get(1).matchStatus()
                );

                await(() -> rooms.gameForPlayer(host.user().id())
                        .map(game -> game.roomState() == vn.ptit.ltm.common.enums.RoomState.FINISHED)
                        .orElse(false));
                var finished = rooms.gameForPlayer(host.user().id()).orElseThrow();
                var winner = finished.participants().stream()
                        .filter(participant -> participant.playerId().equals("1"))
                        .findFirst()
                        .orElseThrow();
                var loser = finished.participants().stream()
                        .filter(participant -> participant.playerId().equals("2"))
                        .findFirst()
                        .orElseThrow();
                assertEquals(roomId, finished.roomId());
                assertEquals(MatchParticipantStatus.COMPLETED, winner.matchStatus());
                assertEquals(1, winner.rank());
                assertEquals(MatchParticipantStatus.FORFEITED, loser.matchStatus());
                assertEquals(2, loser.rank());
                assertEquals(BigDecimal.ZERO, loser.scoreEarned());
                assertTrue(loser.pieces().stream().allMatch(piece -> piece.stepCount() == -1));
                assertTrue(sessions.findByUserId(second.user().id()).isEmpty());
            }
        }
    }

    @Test
    void coalescedSessionExpirationsForfeitAllExpiredBeforeEvaluatingCascading() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(2))) {
            PlayerSession host = session(sessions, 1);
            PlayerSession second = session(sessions, 2);
            PlayerSession third = session(sessions, 3);
            try (RoomService rooms = new RoomService(
                    sessions,
                    new ConnectionRegistry(),
                    () -> 3
            )) {
                startThreePlayerGame(rooms, host, second, third);

                // Mô phỏng event executor quan sát một snapshot đã thiếu đồng thời hai session hết hạn.
                sessions.logout(host.sessionId(), host.connectionId());
                sessions.logout(second.sessionId(), second.connectionId());
                rooms.onSessionsChanged();

                var finished = rooms.gameForPlayer(third.user().id()).orElseThrow();
                assertEquals(vn.ptit.ltm.common.enums.RoomState.FINISHED, finished.roomState());
                assertTrue(finished.participants().stream()
                        .filter(participant -> participant.playerId().equals("1")
                                || participant.playerId().equals("2"))
                        .allMatch(participant -> participant.matchStatus() == MatchParticipantStatus.FORFEITED
                                && BigDecimal.ZERO.compareTo(participant.scoreEarned()) == 0));
                assertEquals(
                        MatchParticipantStatus.COMPLETED,
                        finished.participants().stream()
                                .filter(participant -> participant.playerId().equals("3"))
                                .findFirst()
                                .orElseThrow()
                                .matchStatus()
                );
            }
        }
    }

    @Test
    void requestAtDeadlineIsRejectedAndAppliesTimeoutAtomically() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(2))) {
            PlayerSession host = session(sessions, 1);
            PlayerSession second = session(sessions, 2);
            MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
            try (RoomService rooms = new RoomService(
                    sessions,
                    new ConnectionRegistry(),
                    clock,
                    Duration.ofSeconds(60),
                    () -> 6
            )) {
                String roomId = startTwoPlayerGame(rooms, host, second);
                clock.advance(Duration.ofSeconds(120));

                RoomException expired = assertThrows(
                        RoomException.class,
                        () -> rooms.rollDice(host.sessionId(), host.connectionId(), roomId)
                );
                assertEquals(ErrorCode.TURN_TIMEOUT, expired.errorCode());
                var game = rooms.gameForPlayer(host.user().id()).orElseThrow();
                assertEquals("2", game.currentPlayerId());
                assertEquals(1L, game.stateVersion());
            }
        }
    }

    @Test
    void moveTimeoutAndBonusRollEachGetTwoMinutes() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(2))) {
            PlayerSession host = session(sessions, 1);
            PlayerSession second = session(sessions, 2);
            MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
            try (RoomService rooms = new RoomService(
                    sessions,
                    new ConnectionRegistry(),
                    clock,
                    Duration.ofSeconds(60),
                    () -> 6
            )) {
                String roomId = startTwoPlayerGame(rooms, host, second);
                rooms.rollDice(host.sessionId(), host.connectionId(), roomId);
                var waitingForMove = rooms.gameForPlayer(host.user().id()).orElseThrow();
                assertEquals(TurnState.WAITING_FOR_MOVE, waitingForMove.turnState());
                assertEquals(clock.millis() + 120_000L, waitingForMove.serverDeadlineEpochMillis());

                rooms.movePiece(
                        host.sessionId(),
                        host.connectionId(),
                        roomId,
                        "1-piece-1"
                );
                var bonusRoll = rooms.gameForPlayer(host.user().id()).orElseThrow();
                assertEquals("1", bonusRoll.currentPlayerId());
                assertEquals(TurnState.WAITING_FOR_ROLL, bonusRoll.turnState());
                assertEquals(clock.millis() + 120_000L, bonusRoll.serverDeadlineEpochMillis());

                rooms.rollDice(host.sessionId(), host.connectionId(), roomId);
                clock.advance(Duration.ofMillis(119_999));
                assertNull(rooms.gameForPlayer(host.user().id()).orElseThrow().lastMove());
                rooms.processExpiredTurns();
                assertEquals(TurnState.WAITING_FOR_MOVE,
                        rooms.gameForPlayer(host.user().id()).orElseThrow().turnState());
                clock.advance(Duration.ofMillis(1));
                rooms.processExpiredTurns();
                var afterMoveTimeout = rooms.gameForPlayer(host.user().id()).orElseThrow();
                assertEquals("2", afterMoveTimeout.currentPlayerId());
                assertEquals(TurnState.WAITING_FOR_ROLL, afterMoveTimeout.turnState());
                assertEquals(4L, afterMoveTimeout.stateVersion());
            }
        }
    }

    private static String startTwoPlayerGame(
            RoomService rooms,
            PlayerSession host,
            PlayerSession second
    ) {
        var room = rooms.createRoom(host.sessionId(), host.connectionId());
        rooms.joinRoom(second.sessionId(), second.connectionId(), room.roomId());
        rooms.setReady(host.sessionId(), host.connectionId(), room.roomId(), true);
        rooms.setReady(second.sessionId(), second.connectionId(), room.roomId(), true);
        rooms.startGame(host.sessionId(), host.connectionId(), room.roomId());
        return room.roomId();
    }

    private static String startThreePlayerGame(
            RoomService rooms,
            PlayerSession host,
            PlayerSession second,
            PlayerSession third
    ) {
        var room = rooms.createRoom(host.sessionId(), host.connectionId());
        rooms.joinRoom(second.sessionId(), second.connectionId(), room.roomId());
        rooms.joinRoom(third.sessionId(), third.connectionId(), room.roomId());
        rooms.setReady(host.sessionId(), host.connectionId(), room.roomId(), true);
        rooms.setReady(second.sessionId(), second.connectionId(), room.roomId(), true);
        rooms.setReady(third.sessionId(), third.connectionId(), room.roomId(), true);
        rooms.startGame(host.sessionId(), host.connectionId(), room.roomId());
        return room.roomId();
    }

    private static boolean joinAfterSignal(
            RoomService rooms,
            PlayerSession player,
            String roomId,
            CountDownLatch start
    ) throws InterruptedException {
        start.await();
        try {
            rooms.joinRoom(player.sessionId(), player.connectionId(), roomId);
            return true;
        } catch (RoomException exception) {
            assertEquals(ErrorCode.ROOM_FULL, exception.errorCode());
            return false;
        }
    }

    private static PlayerSession session(SessionManager sessions, long id) {
        return sessions.createSession(user(id), "connection-" + id);
    }

    private static UserAccountRecord user(long id) {
        Instant now = Instant.now();
        return new UserAccountRecord(
                id,
                "user" + id,
                "hash",
                "Player " + id,
                BigDecimal.ZERO,
                0,
                now,
                now
        );
    }

    private static vn.ptit.ltm.common.dto.room.RoomPlayerDto player(
            List<vn.ptit.ltm.common.dto.room.RoomPlayerDto> players,
            String playerId
    ) {
        return players.stream()
                .filter(player -> player.playerId().equals(playerId))
                .findFirst()
                .orElseThrow();
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "Condition was not met before timeout");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
