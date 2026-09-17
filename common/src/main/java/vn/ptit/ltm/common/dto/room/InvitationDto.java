package vn.ptit.ltm.common.dto.room;

import java.util.Objects;

public record InvitationDto(
        String invitationId,
        String roomId,
        String inviterPlayerId,
        String inviterDisplayName,
        long expiresAtEpochMillis
) {
    public InvitationDto {
        Objects.requireNonNull(invitationId, "invitationId");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(inviterPlayerId, "inviterPlayerId");
        Objects.requireNonNull(inviterDisplayName, "inviterDisplayName");
        if (expiresAtEpochMillis <= 0) {
            throw new IllegalArgumentException("expiresAtEpochMillis must be positive");
        }
    }
}
