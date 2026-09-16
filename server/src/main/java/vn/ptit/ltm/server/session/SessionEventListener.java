package vn.ptit.ltm.server.session;

@FunctionalInterface
public interface SessionEventListener {
    void onSessionsChanged();
}
