package vn.ptit.ltm.server.repository;

import java.math.BigDecimal;
import java.time.Instant;

public record UserAccountRecord(
        long id,
        String username,
        String passwordHash,
        String displayName,
        BigDecimal score,
        int firstPlaceCount,
        Instant createdAt,
        Instant updatedAt
) {
}
