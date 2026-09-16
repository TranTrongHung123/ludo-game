package vn.ptit.ltm.server.session;

import vn.ptit.ltm.server.network.ClientConnection;
import vn.ptit.ltm.server.network.ConnectionListener;

import java.util.Objects;

public final class SessionConnectionListener implements ConnectionListener {
    private final SessionManager sessionManager;

    public SessionConnectionListener(SessionManager sessionManager) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
    }

    @Override
    public void onDisconnected(ClientConnection connection) {
        sessionManager.disconnect(connection.id());
    }
}
