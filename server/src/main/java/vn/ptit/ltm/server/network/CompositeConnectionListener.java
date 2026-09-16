package vn.ptit.ltm.server.network;

import java.util.List;
import java.util.Objects;

public final class CompositeConnectionListener implements ConnectionListener {
    private final List<ConnectionListener> listeners;

    public CompositeConnectionListener(ConnectionListener... listeners) {
        Objects.requireNonNull(listeners, "listeners");
        this.listeners = List.of(listeners);
        if (this.listeners.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("listeners must not contain null");
        }
    }

    @Override
    public void onConnected(ClientConnection connection) {
        for (ConnectionListener listener : listeners) {
            listener.onConnected(connection);
        }
    }

    @Override
    public void onDisconnected(ClientConnection connection) {
        RuntimeException firstFailure = null;
        for (ConnectionListener listener : listeners) {
            try {
                listener.onDisconnected(connection);
            } catch (RuntimeException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                } else {
                    firstFailure.addSuppressed(exception);
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }
}
