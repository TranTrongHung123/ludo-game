package vn.ptit.ltm.server.network;

public interface ConnectionListener {
    default void onConnected(ClientConnection connection) {
    }

    default void onDisconnected(ClientConnection connection) {
    }

    static ConnectionListener noop() {
        return new ConnectionListener() {
        };
    }
}
