package vn.ptit.ltm.server.session;

import vn.ptit.ltm.common.enums.PlayerPresenceState;

import java.math.BigDecimal;

public record SessionSnapshot(
        long userId,
        String displayName,
        BigDecimal totalScore,
        int firstPlaceCount,
        PlayerPresenceState presenceState
) {
}
