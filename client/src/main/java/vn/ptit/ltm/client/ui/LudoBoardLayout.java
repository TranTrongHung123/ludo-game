package vn.ptit.ltm.client.ui;

import vn.ptit.ltm.common.dto.game.PieceDto;
import vn.ptit.ltm.common.enums.PieceColor;
import vn.ptit.ltm.common.model.BoardConstants;
import vn.ptit.ltm.common.model.BoardCoordinates;

import java.util.Objects;

/**
 * Chuyển tọa độ authoritative của game sang tọa độ pixel trên bàn cờ JavaFX.
 * Lớp này chỉ phục vụ hiển thị, không tham gia tính nước đi hay thay đổi Game State.
 */
public final class LudoBoardLayout {
    public static final double BOARD_SIZE = 500.0;
    public static final double CELL_SIZE = 28.0;
    public static final double PIECE_SIZE = 24.0;

    private static final double CENTER = BOARD_SIZE / 2.0;
    private static final double RING_RADIUS = 210.0;
    private static final double FINISH_OUTER_RADIUS = 160.0;
    private static final double FINISH_GAP = 24.0;
    private static final double FIRST_ANGLE = -Math.PI / 2.0;
    private static final double QUARTER_TURN = Math.PI / 2.0;
    private static final double YARD_OFFSET = 29.0;

    private LudoBoardLayout() {
    }

    /** Trả về tâm của một ô trên vòng chung 48 ô. */
    public static BoardPoint ringPoint(int globalIndex) {
        if (globalIndex < 0 || globalIndex >= BoardConstants.RING_SIZE) {
            throw new IllegalArgumentException("globalIndex must be between 0 and 47");
        }
        double angle = FIRST_ANGLE
                + (2.0 * Math.PI * globalIndex / BoardConstants.RING_SIZE);
        return radialPoint(angle, RING_RADIUS);
    }

    /** Trả về tâm của nấc 1..6 trên đường về đích của một màu. */
    public static BoardPoint finishPoint(PieceColor color, int finishSlot) {
        Objects.requireNonNull(color, "color");
        if (finishSlot < 1 || finishSlot > BoardConstants.FINISH_TRACK_SIZE) {
            throw new IllegalArgumentException("finishSlot must be between 1 and 6");
        }
        double angle = FIRST_ANGLE + color.slotIndex() * QUARTER_TURN;
        double radius = FINISH_OUTER_RADIUS - (finishSlot - 1) * FINISH_GAP;
        return radialPoint(angle, radius);
    }

    /** Trả về tâm một trong bốn vị trí quân trong chuồng của từng màu. */
    public static BoardPoint yardPoint(PieceColor color, int pieceIndex) {
        Objects.requireNonNull(color, "color");
        if (pieceIndex < 0 || pieceIndex >= BoardConstants.PIECES_PER_PLAYER) {
            throw new IllegalArgumentException("pieceIndex must be between 0 and 3");
        }
        BoardPoint yardCenter = yardCenter(color);
        double horizontal = pieceIndex % 2 == 0 ? -YARD_OFFSET : YARD_OFFSET;
        double vertical = pieceIndex < 2 ? -YARD_OFFSET : YARD_OFFSET;
        return new BoardPoint(yardCenter.x() + horizontal, yardCenter.y() + vertical);
    }

    /**
     * Ánh xạ Piece DTO sang vị trí hiển thị. ON_TRACK luôn đi qua công thức
     * local-step -> global-cell dùng chung, tránh Client tự duy trì tiến độ khác Server.
     */
    public static BoardPoint piecePoint(PieceDto piece, int pieceIndex) {
        Objects.requireNonNull(piece, "piece");
        return switch (piece.state()) {
            case IN_YARD -> yardPoint(piece.color(), pieceIndex);
            case ON_TRACK -> ringPoint(BoardCoordinates.toGlobalCell(piece.color(), piece.stepCount()));
            case IN_FINISH_TRACK, FINISHED -> finishPoint(
                    piece.color(),
                    BoardCoordinates.toFinishTrackSlot(piece.stepCount())
            );
        };
    }

    /** Tâm vùng chuồng màu, dùng để đặt nền và nhãn màu. */
    public static BoardPoint yardCenter(PieceColor color) {
        Objects.requireNonNull(color, "color");
        return switch (color) {
            case RED -> new BoardPoint(152.0, 152.0);
            case BLUE -> new BoardPoint(348.0, 152.0);
            case YELLOW -> new BoardPoint(348.0, 348.0);
            case GREEN -> new BoardPoint(152.0, 348.0);
        };
    }

    private static BoardPoint radialPoint(double angle, double radius) {
        return new BoardPoint(
                CENTER + Math.cos(angle) * radius,
                CENTER + Math.sin(angle) * radius
        );
    }

    public record BoardPoint(double x, double y) {
    }
}
