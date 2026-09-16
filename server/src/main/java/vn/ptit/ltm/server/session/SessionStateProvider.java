package vn.ptit.ltm.server.session;

import vn.ptit.ltm.common.dto.session.ReconnectResult;

@FunctionalInterface
public interface SessionStateProvider {
    ReconnectResult restore(PlayerSession session);

    static SessionStateProvider basic() {
        return session -> new ReconnectResult(true, session.presenceState(), null, null);
    }
}
