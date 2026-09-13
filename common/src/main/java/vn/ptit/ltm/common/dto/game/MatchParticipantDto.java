package vn.ptit.ltm.common.dto.game;

import vn.ptit.ltm.common.enums.MatchParticipantStatus;
import vn.ptit.ltm.common.enums.PieceColor;
import vn.ptit.ltm.common.enums.PlayerPresenceState;
import vn.ptit.ltm.common.model.BoardConstants;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record MatchParticipantDto(
        String playerId,
        String displayName,
        int slotIndex,
        PieceColor color,
        PlayerPresenceState presenceState,
        MatchParticipantStatus matchStatus,
        Integer rank,
        BigDecimal scoreEarned,
        List<PieceDto> pieces
) {
    public MatchParticipantDto {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(presenceState, "presenceState");
        Objects.requireNonNull(matchStatus, "matchStatus");
        pieces = pieces == null ? List.of() : List.copyOf(pieces);
        if (slotIndex < 0 || slotIndex >= BoardConstants.MAX_PLAYERS || color.slotIndex() != slotIndex) {
            throw new IllegalArgumentException("color and slotIndex must identify the same slot");
        }
        if (pieces.size() != BoardConstants.PIECES_PER_PLAYER) {
            throw new IllegalArgumentException("A match participant must have exactly 4 pieces");
        }
        if (pieces.stream().anyMatch(piece -> !piece.ownerPlayerId().equals(playerId) || piece.color() != color)) {
            throw new IllegalArgumentException("Every piece must belong to the participant");
        }
        if (new HashSet<>(pieces.stream().map(PieceDto::pieceId).toList()).size() != pieces.size()) {
            throw new IllegalArgumentException("Piece identifiers must be unique");
        }
        if (rank != null && (rank < 1 || rank > BoardConstants.MAX_PLAYERS)) {
            throw new IllegalArgumentException("rank must be between 1 and 4");
        }
    }
}
