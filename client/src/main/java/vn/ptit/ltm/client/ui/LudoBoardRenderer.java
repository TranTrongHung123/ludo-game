package vn.ptit.ltm.client.ui;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import vn.ptit.ltm.common.dto.game.GameStateDto;
import vn.ptit.ltm.common.dto.game.MatchParticipantDto;
import vn.ptit.ltm.common.dto.game.PieceDto;
import vn.ptit.ltm.common.dto.game.SpecialCellDto;
import vn.ptit.ltm.common.enums.PieceColor;
import vn.ptit.ltm.common.enums.SpecialCellType;
import vn.ptit.ltm.common.model.BoardConstants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Renderer thuần hiển thị cho bàn cờ. Dữ liệu luôn được dựng lại từ GameStateDto
 * do Server gửi; renderer không dự đoán xúc xắc, vị trí mới hoặc kết quả nước đi.
 */
public final class LudoBoardRenderer {
    private static final double YARD_SIZE = 108.0;
    private static final double FINISHED_OFFSET = 7.0;

    private final Pane boardPane;
    private final Pane pieceLayer = new Pane();
    private final Map<Integer, StackPane> ringCells = new HashMap<>();
    private final Map<Integer, Label> ringLabels = new HashMap<>();
    private final Map<Integer, Tooltip> specialTooltips = new HashMap<>();

    public LudoBoardRenderer(Pane boardPane) {
        this.boardPane = Objects.requireNonNull(boardPane, "boardPane");
        configureBoardPane();
        buildStaticBoard();
    }

    /**
     * Render lại ô đặc biệt và toàn bộ quân theo snapshot mới nhất. validPieceIds
     * chỉ tạo highlight; hành động click vẫn chỉ trả Piece DTO cho controller gửi request.
     */
    public void render(
            GameStateDto game,
            String selfPlayerId,
            String selectedPieceId,
            Consumer<PieceDto> onPieceSelected
    ) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(selfPlayerId, "selfPlayerId");
        Objects.requireNonNull(onPieceSelected, "onPieceSelected");
        renderSpecialCells(game.specialCells());
        pieceLayer.getChildren().clear();

        Set<String> validPieceIds = Set.copyOf(game.validPieceIds());
        Map<String, String> ownerNames = new HashMap<>();
        for (MatchParticipantDto participant : game.participants()) {
            ownerNames.put(participant.playerId(), participant.displayName());
            List<PieceDto> pieces = participant.pieces();
            for (int pieceIndex = 0; pieceIndex < pieces.size(); pieceIndex++) {
                PieceDto piece = pieces.get(pieceIndex);
                renderPiece(
                        piece,
                        pieceIndex,
                        ownerNames.get(piece.ownerPlayerId()),
                        selfPlayerId,
                        selectedPieceId,
                        validPieceIds,
                        onPieceSelected
                );
            }
        }
    }

    private void configureBoardPane() {
        boardPane.setMinSize(LudoBoardLayout.BOARD_SIZE, LudoBoardLayout.BOARD_SIZE);
        boardPane.setPrefSize(LudoBoardLayout.BOARD_SIZE, LudoBoardLayout.BOARD_SIZE);
        boardPane.setMaxSize(LudoBoardLayout.BOARD_SIZE, LudoBoardLayout.BOARD_SIZE);
        boardPane.getStyleClass().add("ludo-board");
    }

    /** Dựng phần nền cố định đúng một lần; quân cờ nằm ở layer riêng phía trên. */
    private void buildStaticBoard() {
        for (PieceColor color : PieceColor.values()) {
            buildYard(color);
        }

        Circle home = new Circle(28.0);
        home.setCenterX(LudoBoardLayout.BOARD_SIZE / 2.0);
        home.setCenterY(LudoBoardLayout.BOARD_SIZE / 2.0);
        home.getStyleClass().add("board-home");
        boardPane.getChildren().add(home);

        for (PieceColor color : PieceColor.values()) {
            for (int slot = 1; slot <= BoardConstants.FINISH_TRACK_SIZE; slot++) {
                StackPane cell = createCell(String.valueOf(slot), "finish-cell", colorClass(color));
                placeCentered(cell, LudoBoardLayout.finishPoint(color, slot), LudoBoardLayout.CELL_SIZE);
                boardPane.getChildren().add(cell);
            }
        }

        for (int index = 0; index < BoardConstants.RING_SIZE; index++) {
            Label label = new Label(String.format("%02d", index));
            label.getStyleClass().add("track-cell-label");
            StackPane cell = new StackPane(label);
            cell.setAlignment(Pos.CENTER);
            cell.getStyleClass().add("track-cell");
            PieceColor spawnColor = spawnColor(index);
            if (spawnColor != null) {
                cell.getStyleClass().add("spawn-" + colorClass(spawnColor));
            }
            placeCentered(cell, LudoBoardLayout.ringPoint(index), LudoBoardLayout.CELL_SIZE);
            ringCells.put(index, cell);
            ringLabels.put(index, label);
            boardPane.getChildren().add(cell);
        }

        pieceLayer.setMinSize(LudoBoardLayout.BOARD_SIZE, LudoBoardLayout.BOARD_SIZE);
        pieceLayer.setPrefSize(LudoBoardLayout.BOARD_SIZE, LudoBoardLayout.BOARD_SIZE);
        pieceLayer.setPickOnBounds(false);
        boardPane.getChildren().add(pieceLayer);
    }

    private void buildYard(PieceColor color) {
        LudoBoardLayout.BoardPoint center = LudoBoardLayout.yardCenter(color);
        Region yard = new Region();
        yard.resizeRelocate(
                center.x() - YARD_SIZE / 2.0,
                center.y() - YARD_SIZE / 2.0,
                YARD_SIZE,
                YARD_SIZE
        );
        yard.setMinSize(YARD_SIZE, YARD_SIZE);
        yard.setPrefSize(YARD_SIZE, YARD_SIZE);
        yard.setMaxSize(YARD_SIZE, YARD_SIZE);
        yard.getStyleClass().addAll("piece-yard", "yard-" + colorClass(color));
        boardPane.getChildren().add(yard);

        Label label = new Label(colorName(color));
        label.getStyleClass().addAll("yard-label", "text-" + colorClass(color));
        label.relocate(center.x() - 25.0, center.y() - 10.0);
        boardPane.getChildren().add(label);
    }

    private void renderSpecialCells(List<SpecialCellDto> specialCells) {
        Map<Integer, SpecialCellType> typeByIndex = new HashMap<>();
        for (SpecialCellDto specialCell : specialCells) {
            typeByIndex.put(specialCell.globalIndex(), specialCell.type());
        }

        for (int index = 0; index < BoardConstants.RING_SIZE; index++) {
            StackPane cell = ringCells.get(index);
            cell.getStyleClass().removeIf(style -> style.startsWith("special-"));
            Tooltip previousTooltip = specialTooltips.remove(index);
            if (previousTooltip != null) {
                Tooltip.uninstall(cell, previousTooltip);
            }
            SpecialCellType type = typeByIndex.get(index);
            Label label = ringLabels.get(index);
            if (type == null) {
                label.setText(String.format("%02d", index));
                continue;
            }
            cell.getStyleClass().add("special-" + type.name().toLowerCase());
            label.setText(specialSymbol(type));
            Tooltip tooltip = new Tooltip("Ô " + index + " • " + specialName(type));
            specialTooltips.put(index, tooltip);
            Tooltip.install(cell, tooltip);
        }
    }

    private void renderPiece(
            PieceDto piece,
            int pieceIndex,
            String ownerName,
            String selfPlayerId,
            String selectedPieceId,
            Set<String> validPieceIds,
            Consumer<PieceDto> onPieceSelected
    ) {
        LudoBoardLayout.BoardPoint point = LudoBoardLayout.piecePoint(piece, pieceIndex);
        if (piece.state() == vn.ptit.ltm.common.enums.PieceState.FINISHED) {
            point = offsetFinishedPoint(point, pieceIndex);
        }

        Label number = new Label(String.valueOf(pieceIndex + 1));
        number.getStyleClass().add("piece-number");
        StackPane pieceNode = new StackPane(number);
        pieceNode.setAlignment(Pos.CENTER);
        pieceNode.getStyleClass().addAll("board-piece", "piece-" + colorClass(piece.color()));

        boolean valid = validPieceIds.contains(piece.pieceId());
        if (valid) {
            pieceNode.getStyleClass().add("piece-valid");
        }
        if (piece.pieceId().equals(selectedPieceId)) {
            pieceNode.getStyleClass().add("piece-selected");
        }
        if (piece.slowed()) {
            pieceNode.getStyleClass().add("piece-slowed");
        }
        if (piece.shielded()) {
            pieceNode.getStyleClass().add("piece-shielded");
        }

        boolean selectable = valid && piece.ownerPlayerId().equals(selfPlayerId);
        if (selectable) {
            pieceNode.setCursor(Cursor.HAND);
            pieceNode.setOnMouseClicked(event -> onPieceSelected.accept(piece));
        }
        String description = ownerName + " • quân " + (pieceIndex + 1)
                + " • " + piece.state()
                + (piece.slowed() ? " • đang bị làm chậm" : "")
                + (piece.shielded() ? " • có khiên" : "");
        pieceNode.setAccessibleText(description);
        Tooltip.install(pieceNode, new Tooltip(description));
        placeCentered(pieceNode, point, LudoBoardLayout.PIECE_SIZE);
        pieceLayer.getChildren().add(pieceNode);
    }

    private static StackPane createCell(String text, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().add("track-cell-label");
        StackPane cell = new StackPane(label);
        cell.setAlignment(Pos.CENTER);
        cell.getStyleClass().addAll(styleClasses);
        return cell;
    }

    private static void placeCentered(
            Region node,
            LudoBoardLayout.BoardPoint point,
            double size
    ) {
        node.setMinSize(size, size);
        node.setPrefSize(size, size);
        node.setMaxSize(size, size);
        node.resizeRelocate(point.x() - size / 2.0, point.y() - size / 2.0, size, size);
    }

    private static LudoBoardLayout.BoardPoint offsetFinishedPoint(
            LudoBoardLayout.BoardPoint point,
            int pieceIndex
    ) {
        double horizontal = pieceIndex % 2 == 0 ? -FINISHED_OFFSET : FINISHED_OFFSET;
        double vertical = pieceIndex < 2 ? -FINISHED_OFFSET : FINISHED_OFFSET;
        return new LudoBoardLayout.BoardPoint(point.x() + horizontal, point.y() + vertical);
    }

    private static PieceColor spawnColor(int globalIndex) {
        for (PieceColor color : PieceColor.values()) {
            if (color.startIndex() == globalIndex) {
                return color;
            }
        }
        return null;
    }

    private static String colorClass(PieceColor color) {
        return color.name().toLowerCase();
    }

    private static String colorName(PieceColor color) {
        return switch (color) {
            case RED -> "ĐỎ";
            case BLUE -> "XANH DƯƠNG";
            case YELLOW -> "VÀNG";
            case GREEN -> "XANH LÁ";
        };
    }

    private static String specialSymbol(SpecialCellType type) {
        return switch (type) {
            case SPEED -> "+2";
            case SLOW -> "−1";
            case LUCKY -> "★";
            case TRAP -> "↩";
            case SHIELD -> "◆";
        };
    }

    private static String specialName(SpecialCellType type) {
        return switch (type) {
            case SPEED -> "Tăng tốc +2";
            case SLOW -> "Làm chậm";
            case LUCKY -> "May mắn";
            case TRAP -> "Bẫy";
            case SHIELD -> "Khiên";
        };
    }
}
