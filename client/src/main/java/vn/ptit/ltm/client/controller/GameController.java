package vn.ptit.ltm.client.controller;

import javafx.application.Platform;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import vn.ptit.ltm.client.service.AuthClientService;
import vn.ptit.ltm.client.state.ClientSessionState;
import vn.ptit.ltm.client.state.ConnectionState;
import vn.ptit.ltm.client.ui.LudoBoardRenderer;
import vn.ptit.ltm.client.ui.SceneNavigator;
import vn.ptit.ltm.client.util.UiErrorMessages;
import vn.ptit.ltm.common.dto.game.GameStateDto;
import vn.ptit.ltm.common.dto.game.MatchParticipantDto;
import vn.ptit.ltm.common.dto.game.PieceDto;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.enums.MatchParticipantStatus;
import vn.ptit.ltm.common.enums.RoomState;
import vn.ptit.ltm.common.enums.TurnState;
import vn.ptit.ltm.common.model.BoardCoordinates;

import java.util.List;
import java.util.Objects;

public final class GameController implements ConnectionAwareController {
    @FXML
    private Label matchIdLabel;
    @FXML
    private Label turnLabel;
    @FXML
    private Label phaseLabel;
    @FXML
    private Label connectionLabel;
    @FXML
    private Label diceLabel;
    @FXML
    private Label feedbackLabel;
    @FXML
    private ListView<String> participantsList;
    @FXML
    private ListView<PieceDto> piecesList;
    @FXML
    private Pane boardPane;
    @FXML
    private Button rollButton;
    @FXML
    private Button moveButton;
    @FXML
    private Button leaveGameButton;

    private AuthClientService authService;
    private ClientSessionState sessionState;
    private SceneNavigator navigator;
    private Runnable removeGameListener;
    private Runnable removeGameUpdatedListener;
    private Runnable removeDiceListener;
    private Runnable removeTimeoutListener;
    private Runnable removeGameOverListener;
    private Timeline countdownTimeline;
    private LudoBoardRenderer boardRenderer;
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private boolean busy;

    public void configure(
            AuthClientService authService,
            ClientSessionState sessionState,
            SceneNavigator navigator
    ) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.sessionState = Objects.requireNonNull(sessionState, "sessionState");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        sessionState.gameState()
                .orElseThrow(() -> new IllegalStateException("Game view requires a game state"));
        boardRenderer = new LudoBoardRenderer(boardPane);
        removeGameListener = authService.addStateListener(
                MessageType.GAME_STATE,
                () -> Platform.runLater(this::render)
        );
        removeGameUpdatedListener = authService.addStateListener(
                MessageType.GAME_STATE_UPDATED,
                () -> Platform.runLater(this::render)
        );
        removeDiceListener = authService.addStateListener(
                MessageType.DICE_RESULT,
                () -> Platform.runLater(this::render)
        );
        removeTimeoutListener = authService.addStateListener(
                MessageType.TURN_TIMEOUT,
                () -> Platform.runLater(this::renderTimeout)
        );
        removeGameOverListener = authService.addStateListener(
                MessageType.GAME_OVER,
                () -> Platform.runLater(this::renderGameOver)
        );
        piecesList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(PieceDto piece, boolean empty) {
                super.updateItem(piece, empty);
                setText(empty || piece == null ? null : formatPiece(piece));
            }
        });
        piecesList.getSelectionModel().selectedItemProperty()
                .addListener((ignored, previous, selected) -> {
                    renderBoard();
                    refreshActions();
                });
        render();
        countdownTimeline = new Timeline(new KeyFrame(Duration.millis(250), ignored -> {
            renderCountdown();
            refreshActions();
        }));
        countdownTimeline.setCycleCount(Animation.INDEFINITE);
        countdownTimeline.play();
        onConnectionStateChanged(authService.connectionState());
    }

    private void render() {
        GameStateDto game = sessionState.gameState().orElse(null);
        if (game == null) {
            return;
        }
        matchIdLabel.setText(game.matchId());
        String currentName = game.participants().stream()
                .filter(participant -> participant.playerId().equals(game.currentPlayerId()))
                .map(MatchParticipantDto::displayName)
                .findFirst()
                .orElse(game.currentPlayerId());
        turnLabel.setText(game.turnState() == TurnState.FINISHED
                ? "Trận đấu đã kết thúc"
                : "Lượt hiện tại: " + currentName + " • Slot " + game.currentSlot());
        renderCountdown(game);
        diceLabel.setText(sessionState.lastDiceResult()
                .map(result -> "Xúc xắc gần nhất: " + result.diceValue()
                        + (result.hasValidMoves() ? "" : " • không có nước đi"))
                .orElse("Xúc xắc: chưa đổ"));
        participantsList.getItems().setAll(game.participants().stream()
                .map(GameController::formatParticipant)
                .toList());
        String selfId = selfPlayerId();
        String selectedPieceId = selectedPieceId();
        List<PieceDto> ownPieces = game.participants().stream()
                .filter(participant -> participant.playerId().equals(selfId))
                .findFirst()
                .map(MatchParticipantDto::pieces)
                .orElse(List.of());
        piecesList.getItems().setAll(ownPieces);
        ownPieces.stream()
                .filter(piece -> piece.pieceId().equals(selectedPieceId))
                .findFirst()
                .ifPresent(piece -> piecesList.getSelectionModel().select(piece));
        renderBoard(game);
        refreshActions();
    }

    @FXML
    private void handleRollDice() {
        GameStateDto game = sessionState.gameState().orElse(null);
        if (game == null) {
            return;
        }
        setBusy(true);
        showFeedback("Server đang đổ xúc xắc...", false);
        authService.rollDice(game.roomId()).whenComplete((result, failure) -> Platform.runLater(() -> {
            setBusy(false);
            if (failure != null) {
                showFeedback(UiErrorMessages.from(failure), true);
                return;
            }
            showFeedback(
                    result.hasValidMoves()
                            ? "Bạn đổ được " + result.diceValue() + ". Hãy chọn quân hợp lệ."
                            : "Bạn đổ được " + result.diceValue() + " nhưng không có nước đi hợp lệ.",
                    false
            );
            render();
        }));
    }

    @FXML
    private void handleMovePiece() {
        GameStateDto game = sessionState.gameState().orElse(null);
        PieceDto selected = piecesList.getSelectionModel().getSelectedItem();
        if (game == null || selected == null) {
            showFeedback("Hãy chọn một quân hợp lệ.", true);
            return;
        }
        setBusy(true);
        showFeedback("Server đang xử lý nước đi...", false);
        authService.movePiece(game.roomId(), selected.pieceId())
                .whenComplete((result, failure) -> Platform.runLater(() -> {
                    setBusy(false);
                    if (failure != null) {
                        showFeedback(UiErrorMessages.from(failure), true);
                        return;
                    }
                    String capture = result.capturedPieceId() == null
                            ? ""
                            : " Đã đá quân " + result.capturedPieceId() + ".";
                    String bonus = result.bonusRoll() ? " Bạn được đổ thêm một lần." : "";
                    showFeedback("Đã di chuyển " + result.piece().pieceId() + "." + capture + bonus, false);
                    render();
                }));
    }

    @FXML
    private void handleLeaveGame() {
        GameStateDto game = sessionState.gameState().orElse(null);
        if (game == null) {
            navigator.showLobby();
            return;
        }
        if (requiresForfeitConfirmation(game) && !confirmForfeit()) {
            return;
        }

        setBusy(true);
        showFeedback(
                requiresForfeitConfirmation(game)
                        ? "Server đang xử lý bỏ cuộc..."
                        : "Đang rời phòng...",
                false
        );
        authService.leaveRoom(game.roomId()).whenComplete((ignored, failure) -> Platform.runLater(() -> {
            setBusy(false);
            if (failure != null) {
                showFeedback(UiErrorMessages.from(failure), true);
                return;
            }
            navigator.showLobby();
        }));
    }

    @Override
    public void onConnectionStateChanged(ConnectionState state) {
        connectionState = state;
        switch (state) {
            case CONNECTED -> connectionLabel.setText("Đã kết nối Game Server");
            case CONNECTING -> connectionLabel.setText("Đang kết nối Game Server...");
            case DISCONNECTED -> connectionLabel.setText("Mất kết nối — grace period 60 giây");
        }
        connectionLabel.getStyleClass().removeAll("connection-online", "connection-offline");
        connectionLabel.getStyleClass().add(
                state == ConnectionState.CONNECTED ? "connection-online" : "connection-offline"
        );
        refreshActions();
    }

    @Override
    public void dispose() {
        if (removeGameListener != null) {
            removeGameListener.run();
            removeGameListener = null;
        }
        if (removeGameUpdatedListener != null) {
            removeGameUpdatedListener.run();
            removeGameUpdatedListener = null;
        }
        if (removeDiceListener != null) {
            removeDiceListener.run();
            removeDiceListener = null;
        }
        if (removeTimeoutListener != null) {
            removeTimeoutListener.run();
            removeTimeoutListener = null;
        }
        if (removeGameOverListener != null) {
            removeGameOverListener.run();
            removeGameOverListener = null;
        }
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
    }

    private static String formatParticipant(MatchParticipantDto participant) {
        long yardPieces = participant.pieces().stream()
                .filter(piece -> piece.stepCount() == -1)
                .count();
        return "Slot " + participant.slotIndex()
                + " • " + participant.color()
                + " • " + participant.displayName()
                + " • " + participant.matchStatus()
                + " • " + yardPieces + "/4 quân trong chuồng";
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        refreshActions();
    }

    private void refreshActions() {
        if (rollButton == null || moveButton == null || leaveGameButton == null || sessionState == null) {
            return;
        }
        GameStateDto game = sessionState.gameState().orElse(null);
        PieceDto selected = piecesList.getSelectionModel().getSelectedItem();
        boolean connected = connectionState == ConnectionState.CONNECTED;
        boolean ownTurn = game != null && selfPlayerId().equals(game.currentPlayerId());
        boolean playing = game != null && game.roomState() == RoomState.PLAYING;
        boolean beforeDeadline = game != null
                && game.serverDeadlineEpochMillis() != null
                && System.currentTimeMillis() < game.serverDeadlineEpochMillis();
        rollButton.setDisable(busy || !connected || !playing || !ownTurn || !beforeDeadline
                || game.turnState() != TurnState.WAITING_FOR_ROLL);
        moveButton.setDisable(busy || !connected || !playing || !ownTurn || !beforeDeadline
                || game.turnState() != TurnState.WAITING_FOR_MOVE
                || selected == null
                || !game.validPieceIds().contains(selected.pieceId()));
        boolean activeSelf = game != null && game.participants().stream().anyMatch(participant ->
                participant.playerId().equals(selfPlayerId())
                        && participant.matchStatus() == MatchParticipantStatus.ACTIVE
        );
        leaveGameButton.setText(playing && activeSelf ? "Bỏ cuộc" : "Về sảnh");
        leaveGameButton.setDisable(busy || !connected || game == null);
    }

    private String selfPlayerId() {
        return sessionState.profile().map(profile -> profile.playerId()).orElse("");
    }

    /**
     * Đồng bộ lựa chọn từ quân trên bàn với danh sách chi tiết. Việc chọn không tự
     * di chuyển quân; nút Di chuyển vẫn gửi duy nhất pieceId để Server xác nhận.
     */
    private void selectPieceFromBoard(PieceDto piece) {
        piecesList.getSelectionModel().select(piece);
        piecesList.scrollTo(piece);
        renderBoard();
        refreshActions();
    }

    private void renderBoard() {
        sessionState.gameState().ifPresent(this::renderBoard);
    }

    private void renderBoard(GameStateDto game) {
        if (boardRenderer == null) {
            return;
        }
        boardRenderer.render(
                game,
                selfPlayerId(),
                selectedPieceId(),
                this::selectPieceFromBoard
        );
    }

    private String selectedPieceId() {
        PieceDto selected = piecesList == null
                ? null
                : piecesList.getSelectionModel().getSelectedItem();
        return selected == null ? "" : selected.pieceId();
    }

    private boolean requiresForfeitConfirmation(GameStateDto game) {
        return game.roomState() == RoomState.PLAYING
                && game.participants().stream().anyMatch(participant ->
                participant.playerId().equals(selfPlayerId())
                        && participant.matchStatus() == MatchParticipantStatus.ACTIVE
        );
    }

    /**
     * Quit là hành động chủ động nên phải xác nhận rõ: Server forfeit ngay, không mở
     * reconnect grace period và điểm của người bỏ cuộc bằng 0.
     */
    private boolean confirmForfeit() {
        ButtonType forfeit = new ButtonType("Bỏ cuộc", ButtonBar.ButtonData.OK_DONE);
        ButtonType stay = new ButtonType("Ở lại", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert confirmation = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Bạn sẽ bị FORFEITED ngay, nhận 0 điểm và không có grace period để quay lại trận.",
                forfeit,
                stay
        );
        confirmation.setTitle("Xác nhận bỏ cuộc");
        confirmation.setHeaderText("Rời trận đấu đang diễn ra?");
        if (leaveGameButton.getScene() != null) {
            confirmation.initOwner(leaveGameButton.getScene().getWindow());
        }
        return confirmation.showAndWait().filter(forfeit::equals).isPresent();
    }

    private void showFeedback(String message, boolean error) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().setAll(error ? "feedback-error" : "feedback-success");
    }

    private void renderCountdown() {
        sessionState.gameState().ifPresent(this::renderCountdown);
    }

    private void renderCountdown(GameStateDto game) {
        if (game.turnState() == TurnState.FINISHED || game.serverDeadlineEpochMillis() == null) {
            phaseLabel.setText("Phase: " + game.turnState());
            return;
        }
        long remainingMillis = Math.max(0L, game.serverDeadlineEpochMillis() - System.currentTimeMillis());
        long remainingSeconds = (remainingMillis + 999L) / 1_000L;
        phaseLabel.setText("Phase: " + game.turnState() + " • còn " + remainingSeconds + " giây");
    }

    private void renderTimeout() {
        sessionState.lastTurnTimeout().ifPresent(timeout -> showFeedback(
                "Người chơi " + timeout.playerId() + " đã hết thời gian "
                        + (timeout.timedOutState() == TurnState.WAITING_FOR_ROLL
                        ? "đổ xúc xắc."
                        : "chọn quân."),
                true
        ));
    }

    /** Chuyển sang màn hình kết quả với bảng hạng authoritative do Server gửi. */
    private void renderGameOver() {
        render();
        if (sessionState.gameOver().isPresent()) {
            navigator.showGameResult();
        }
    }

    private static String formatPiece(PieceDto piece) {
        String position = switch (piece.state()) {
            case IN_YARD -> "Trong chuồng";
            case ON_TRACK -> "Bước " + piece.stepCount() + " • ô "
                    + BoardCoordinates.toGlobalCell(piece.color(), piece.stepCount());
            case IN_FINISH_TRACK -> "Đích " + BoardCoordinates.toFinishTrackSlot(piece.stepCount());
            case FINISHED -> "Đã hoàn thành";
        };
        return piece.pieceId() + " • " + position
                + (piece.slowed() ? " • Slow" : "")
                + (piece.shielded() ? " • Shield" : "");
    }
}
