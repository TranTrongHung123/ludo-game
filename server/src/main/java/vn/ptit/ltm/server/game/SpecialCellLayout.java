package vn.ptit.ltm.server.game;

import vn.ptit.ltm.common.dto.game.SpecialCellDto;
import vn.ptit.ltm.common.enums.SpecialCellType;

import java.util.List;

/**
 * Canonical, rotationally symmetric special-cell layout for every match.
 */
public final class SpecialCellLayout {
    private static final List<SpecialCellDto> CANONICAL = List.of(
            cell(2, SpecialCellType.SPEED),
            cell(4, SpecialCellType.SLOW),
            cell(6, SpecialCellType.LUCKY),
            cell(8, SpecialCellType.TRAP),
            cell(14, SpecialCellType.SPEED),
            cell(16, SpecialCellType.SLOW),
            cell(18, SpecialCellType.LUCKY),
            cell(20, SpecialCellType.TRAP),
            cell(26, SpecialCellType.SPEED),
            cell(28, SpecialCellType.SLOW),
            cell(30, SpecialCellType.LUCKY),
            cell(32, SpecialCellType.TRAP),
            cell(38, SpecialCellType.SPEED),
            cell(40, SpecialCellType.SLOW),
            cell(42, SpecialCellType.LUCKY),
            cell(44, SpecialCellType.TRAP)
    );

    private SpecialCellLayout() {
    }

    public static List<SpecialCellDto> canonical() {
        return CANONICAL;
    }

    private static SpecialCellDto cell(int globalIndex, SpecialCellType type) {
        return new SpecialCellDto(globalIndex, type);
    }
}
