package vn.ptit.ltm.server.network;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ConnectionRegistry implements ConnectionListener {
    private final ConcurrentMap<String, ClientConnection> connections = new ConcurrentHashMap<>();

    @Override
    public void onConnected(ClientConnection connection) {
        connections.put(connection.id(), connection);
    }

    @Override
    public void onDisconnected(ClientConnection connection) {
        connections.remove(connection.id(), connection);
    }

    public Optional<ClientConnection> find(String connectionId) {
        return Optional.ofNullable(connections.get(connectionId));
    }

    public List<ClientConnection> snapshot() {
        return List.copyOf(connections.values());
    }

    public int size() {
        return connections.size();
    }
}
