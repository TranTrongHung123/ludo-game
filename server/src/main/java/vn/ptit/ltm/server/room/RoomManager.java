package vn.ptit.ltm.server.room;

import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.server.session.PlayerSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class RoomManager {
    private final ConcurrentMap<String, GameRoom> roomsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, String> roomIdByUserId = new ConcurrentHashMap<>();

    GameRoom create(PlayerSession creator) {
        long userId = creator.user().id();
        String roomId = UUID.randomUUID().toString();
        GameRoom room = new GameRoom(roomId, creator);
        roomsById.put(roomId, room);
        String existingRoomId = roomIdByUserId.putIfAbsent(userId, roomId);
        if (existingRoomId != null) {
            roomsById.remove(roomId, room);
            throw new RoomException(ErrorCode.ALREADY_IN_ROOM, "Player is already in a room");
        }
        return room;
    }

    GameRoom join(String roomId, PlayerSession player) {
        GameRoom room = roomsById.get(roomId);
        if (room == null) {
            throw new RoomException(ErrorCode.ROOM_NOT_FOUND, "Room does not exist");
        }
        long userId = player.user().id();
        String existingRoomId = roomIdByUserId.putIfAbsent(userId, roomId);
        if (existingRoomId != null) {
            throw new RoomException(ErrorCode.ALREADY_IN_ROOM, "Player is already in a room");
        }
        try {
            room.add(player);
            return room;
        } catch (RuntimeException exception) {
            roomIdByUserId.remove(userId, roomId);
            throw exception;
        }
    }

    GameRoom requireRoomForPlayer(String roomId, long userId) {
        String currentRoomId = roomIdByUserId.get(userId);
        if (currentRoomId == null || !currentRoomId.equals(roomId)) {
            throw new RoomException(ErrorCode.NOT_IN_ROOM, "Player is not in this room");
        }
        GameRoom room = roomsById.get(roomId);
        if (room == null || !room.hasPlayer(userId)) {
            roomIdByUserId.remove(userId, roomId);
            throw new RoomException(ErrorCode.ROOM_NOT_FOUND, "Room does not exist");
        }
        return room;
    }

    Optional<GameRoom> leaveCurrent(long userId) {
        String roomId = roomIdByUserId.get(userId);
        if (roomId == null) {
            return Optional.empty();
        }
        GameRoom room = roomsById.get(roomId);
        if (room == null) {
            roomIdByUserId.remove(userId, roomId);
            return Optional.empty();
        }
        if (!room.removeIfWaiting(userId)) {
            return Optional.empty();
        }
        roomIdByUserId.remove(userId, roomId);
        if (room.isClosed()) {
            roomsById.remove(roomId, room);
        }
        return Optional.of(room);
    }

    /**
     * Gỡ mapping của người đã Quit hoặc rời một trận kết thúc. GameRoom vẫn giữ
     * dữ liệu participant lịch sử cho tới khi thành viên sống cuối cùng rời phòng.
     */
    Optional<GameRoom> departCurrent(long userId) {
        String roomId = roomIdByUserId.get(userId);
        if (roomId == null) {
            return Optional.empty();
        }
        GameRoom room = roomsById.get(roomId);
        if (room == null) {
            roomIdByUserId.remove(userId, roomId);
            return Optional.empty();
        }
        if (!room.departAfterStart(userId)) {
            return Optional.empty();
        }
        roomIdByUserId.remove(userId, roomId);
        if (room.isClosed()) {
            roomsById.remove(roomId, room);
        }
        return Optional.of(room);
    }

    Optional<GameRoom> findByPlayer(long userId) {
        String roomId = roomIdByUserId.get(userId);
        if (roomId == null) {
            return Optional.empty();
        }
        GameRoom room = roomsById.get(roomId);
        if (room == null || !room.hasPlayer(userId)) {
            roomIdByUserId.remove(userId, roomId);
            return Optional.empty();
        }
        return Optional.of(room);
    }

    Optional<GameRoom> findById(String roomId) {
        return Optional.ofNullable(roomsById.get(roomId));
    }

    List<GameRoom> snapshot() {
        return List.copyOf(roomsById.values());
    }
}
