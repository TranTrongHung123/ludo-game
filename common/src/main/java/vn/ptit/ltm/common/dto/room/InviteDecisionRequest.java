package vn.ptit.ltm.common.dto.room;

import java.util.Objects;

public record InviteDecisionRequest(String invitationId) {
    public InviteDecisionRequest {
        Objects.requireNonNull(invitationId, "invitationId");
    }
}
