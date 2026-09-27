package vn.ptit.ltm.server.session;

import vn.ptit.ltm.common.dto.session.ReconnectResult;

@FunctionalInterface
public interface SessionStateProvider {
    ReconnectResult restore(PlayerSession session);

    // Cung cấp kết quả reconnect chỉ gồm presence khi không có dữ liệu phòng.
    static SessionStateProvider basic() {
        return session -> new ReconnectResult(true, session.presenceState(), null, null);
    }
}
