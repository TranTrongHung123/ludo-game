package vn.ptit.ltm.common.dto.game;

import vn.ptit.ltm.common.enums.SpecialCellType;

// Các mốc di chuyển do Server xác nhận chỉ dùng để trình diễn, không làm đầu vào cho luật game.
public record MovePresentationDto(
        String pieceId,
        int fromStep,
        int landedStep,
        int toStep,
        SpecialCellType triggeredEffect
) {}
