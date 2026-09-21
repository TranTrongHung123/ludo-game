package vn.ptit.ltm.server.room;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.dto.chat.ChatMessageDto;
import vn.ptit.ltm.common.enums.PieceColor;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.server.network.ConnectionRegistry;
import vn.ptit.ltm.server.repository.UserAccountRecord;
import vn.ptit.ltm.server.session.PlayerSession;
import vn.ptit.ltm.server.session.SessionManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RoomServiceChatTest {

    @Test
    void sendChatMessageSuccessAndValidatesSenderMetadata() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(60))) {
            PlayerSession host = session(sessions, 1, "HostPlayer");
            PlayerSession second = session(sessions, 2, "SecondPlayer");
            RoomService rooms = new RoomService(sessions, new ConnectionRegistry());

            var room = rooms.createRoom(host.sessionId(), host.connectionId());
            rooms.joinRoom(second.sessionId(), second.connectionId(), room.roomId());

            ChatMessageDto hostMsg = rooms.sendChatMessage(
                    host.sessionId(),
                    host.connectionId(),
                    room.roomId(),
                    "Hello from host!"
            );

            assertNotNull(hostMsg.messageId());
            assertEquals(room.roomId(), hostMsg.roomId());
            assertEquals("1", hostMsg.senderId());
            assertEquals("HostPlayer", hostMsg.senderDisplayName());
            assertEquals(0, hostMsg.senderSlotIndex());
            assertEquals(PieceColor.RED, hostMsg.senderColor());
            assertEquals("Hello from host!", hostMsg.message());
            assertTrue(hostMsg.timestampEpochMillis() > 0);

            ChatMessageDto secondMsg = rooms.sendChatMessage(
                    second.sessionId(),
                    second.connectionId(),
                    room.roomId(),
                    "   Hi host!   "
            );
            assertEquals("SecondPlayer", secondMsg.senderDisplayName());
            assertEquals(1, secondMsg.senderSlotIndex());
            assertEquals(PieceColor.BLUE, secondMsg.senderColor());
            assertEquals("Hi host!", secondMsg.message()); // trimmed
        }
    }

    @Test
    void rejectsEmptyOrOversizedMessage() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(60))) {
            PlayerSession host = session(sessions, 1, "HostPlayer");
            RoomService rooms = new RoomService(sessions, new ConnectionRegistry());
            var room = rooms.createRoom(host.sessionId(), host.connectionId());

            RoomException emptyEx = assertThrows(RoomException.class, () ->
                    rooms.sendChatMessage(host.sessionId(), host.connectionId(), room.roomId(), "")
            );
            assertEquals(ErrorCode.INVALID_REQUEST, emptyEx.errorCode());

            RoomException blankEx = assertThrows(RoomException.class, () ->
                    rooms.sendChatMessage(host.sessionId(), host.connectionId(), room.roomId(), "    ")
            );
            assertEquals(ErrorCode.INVALID_REQUEST, blankEx.errorCode());

            RoomException longEx = assertThrows(RoomException.class, () ->
                    rooms.sendChatMessage(host.sessionId(), host.connectionId(), room.roomId(), "x".repeat(201))
            );
            assertEquals(ErrorCode.INVALID_REQUEST, longEx.errorCode());
        }
    }

    @Test
    void rejectsNonMemberAndNonExistentRoom() {
        try (SessionManager sessions = new SessionManager(Duration.ofSeconds(60))) {
            PlayerSession host = session(sessions, 1, "HostPlayer");
            PlayerSession outsider = session(sessions, 2, "Outsider");
            RoomService rooms = new RoomService(sessions, new ConnectionRegistry());
            var room = rooms.createRoom(host.sessionId(), host.connectionId());

            RoomException notInRoom = assertThrows(RoomException.class, () ->
                    rooms.sendChatMessage(outsider.sessionId(), outsider.connectionId(), room.roomId(), "Hey!")
            );
            assertEquals(ErrorCode.NOT_IN_ROOM, notInRoom.errorCode());

            RoomException noRoom = assertThrows(RoomException.class, () ->
                    rooms.sendChatMessage(host.sessionId(), host.connectionId(), "non-existent-room", "Hey!")
            );
            assertEquals(ErrorCode.ROOM_NOT_FOUND, noRoom.errorCode());
        }
    }

    private static PlayerSession session(SessionManager sessions, long userId, String displayName) {
        Instant now = Instant.now();
        UserAccountRecord user = new UserAccountRecord(
                userId,
                "user" + userId,
                "hash",
                displayName,
                BigDecimal.ZERO,
                0,
                now,
                now
        );
        return sessions.createSession(user, "conn-" + userId);
    }
}
