package vn.ptit.ltm.common.dto.game;

import vn.ptit.ltm.common.enums.SpecialCellType;

/** Server-confirmed waypoints for presentation only; never an input to game rules. */
public record MovePresentationDto(
        String pieceId,
        int fromStep,
        int landedStep,
        int toStep,
        SpecialCellType triggeredEffect
) {}
