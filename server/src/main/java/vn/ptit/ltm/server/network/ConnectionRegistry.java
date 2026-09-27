package vn.ptit.ltm.server.network;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ConnectionRegistry implements ConnectionListener {
    private final ConcurrentMap<String, ClientConnection> connections = new ConcurrentHashMap<>();

    // Lưu connection theo ID để gửi sự kiện đúng người nhận.
    @Override
    public void onConnected(ClientConnection connection) {
        connections.put(connection.id(), connection);
    }

    // Xóa connection đã đóng khỏi danh sách gửi broadcast.
    @Override
    public void onDisconnected(ClientConnection connection) {
        connections.remove(connection.id(), connection);
    }

    public Optional<ClientConnection> find(String connectionId) {
        return Optional.ofNullable(connections.get(connectionId));
    }

    // Chụp danh sách connection để duyệt mà không giữ cấu trúc đang thay đổi.
    public List<ClientConnection> snapshot() {
        return List.copyOf(connections.values());
    }

    public int size() {
        return connections.size();
    }
}
