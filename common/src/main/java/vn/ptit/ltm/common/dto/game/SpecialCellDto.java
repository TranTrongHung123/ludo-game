package vn.ptit.ltm.common.dto.game;

import vn.ptit.ltm.common.enums.SpecialCellType;
import vn.ptit.ltm.common.model.BoardCoordinates;

import java.util.Objects;

public record SpecialCellDto(int globalIndex, SpecialCellType type) {
    public SpecialCellDto {
        Objects.requireNonNull(type, "type");
        if (!BoardCoordinates.isSpecialCellAllowed(globalIndex)) {
            throw new IllegalArgumentException("Special cell is outside the ring or blacklisted: " + globalIndex);
        }
    }
}
