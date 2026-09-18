package vn.ptit.ltm.client.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import vn.ptit.ltm.client.service.AuthClientService;
import vn.ptit.ltm.client.state.ClientSessionState;
import vn.ptit.ltm.client.state.ConnectionState;
import vn.ptit.ltm.client.ui.SceneNavigator;
import vn.ptit.ltm.client.util.UiErrorMessages;
import vn.ptit.ltm.client.util.UiFormatters;
import vn.ptit.ltm.common.dto.ranking.MatchHistoryEntryDto;

import java.util.Objects;

public final class MatchHistoryController implements ConnectionAwareController {
    @FXML
    private Label connectionLabel;
    @FXML
    private Label feedbackLabel;
    @FXML
    private Label summaryLabel;
    @FXML
    private ListView<MatchHistoryEntryDto> historyList;
    @FXML
    private Button refreshButton;

    private AuthClientService authService;
    private SceneNavigator navigator;
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private boolean loading;

    public void configure(
            AuthClientService authService,
            ClientSessionState sessionState,
            SceneNavigator navigator
    ) {
        this.authService = Objects.requireNonNull(authService, "authService");
        Objects.requireNonNull(sessionState, "sessionState");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        historyList.setPlaceholder(new Label("Bạn chưa có trận đấu nào được lưu."));
        historyList.setCellFactory(ignored -> new HistoryCell());
        onConnectionStateChanged(authService.connectionState());
        loadHistory();
    }

    @FXML
    private void handleBack() {
        navigator.showLobby();
    }

    @FXML
    private void handleRefresh() {
        loadHistory();
    }

    @Override
    public void onConnectionStateChanged(ConnectionState state) {
        connectionState = state;
        connectionLabel.setText(switch (state) {
            case CONNECTED -> "Đã kết nối";
            case CONNECTING -> "Đang kết nối...";
            case DISCONNECTED -> "Mất kết nối";
        });
        connectionLabel.getStyleClass().removeAll("connection-online", "connection-offline");
        connectionLabel.getStyleClass().add(
                state == ConnectionState.CONNECTED ? "connection-online" : "connection-offline"
        );
        refreshButton.setDisable(loading || state != ConnectionState.CONNECTED);
    }

    private void loadHistory() {
        if (connectionState != ConnectionState.CONNECTED || loading) {
            return;
        }
        setLoading(true);
        feedbackLabel.setText("Đang tải lịch sử từ Server...");
        authService.getMatchHistory().whenComplete((payload, failure) -> Platform.runLater(() -> {
            setLoading(false);
            if (failure != null) {
                showFeedback(UiErrorMessages.from(failure), true);
                return;
            }
            historyList.getItems().setAll(payload.matches());
            long firstPlaces = payload.matches().stream().filter(match -> match.rank() == 1).count();
            summaryLabel.setText(payload.matches().size() + " trận gần nhất • "
                    + firstPlaces + " lần hạng nhất");
            showFeedback("Lịch sử đã được cập nhật.", false);
        }));
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        refreshButton.setDisable(loading || connectionState != ConnectionState.CONNECTED);
    }

    private void showFeedback(String text, boolean error) {
        feedbackLabel.setText(text);
        feedbackLabel.getStyleClass().setAll(error ? "feedback-error" : "feedback-success");
    }

    private static final class HistoryCell extends ListCell<MatchHistoryEntryDto> {
        @Override
        protected void updateItem(MatchHistoryEntryDto match, boolean empty) {
            super.updateItem(match, empty);
            if (empty || match == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Label rank = new Label("Hạng " + match.rank() + "/" + match.playerCount());
            rank.getStyleClass().add(match.rank() == 1 ? "result-win" : "result-rank");
            Label time = new Label(UiFormatters.dateTime(match.endedAtEpochMillis()));
            time.getStyleClass().add("ranking-name");
            Label metadata = new Label("Quân " + UiFormatters.color(match.color())
                    + " • Match " + match.matchId());
            metadata.getStyleClass().add("screen-subtitle");
            Label score = new Label(UiFormatters.score(match.scoreEarned()) + " điểm");
            score.getStyleClass().add(match.forfeited() ? "result-forfeit" : "ranking-score");
            Label status = new Label(match.forfeited() ? "Bỏ cuộc" : "Hoàn thành");
            status.getStyleClass().add(match.forfeited() ? "result-forfeit" : "screen-subtitle");
            VBox details = new VBox(3.0, time, metadata);
            HBox.setHgrow(details, Priority.ALWAYS);
            VBox outcome = new VBox(3.0, score, status);
            outcome.setAlignment(Pos.CENTER_RIGHT);
            HBox row = new HBox(14.0, rank, details, outcome);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("data-row");
            setGraphic(row);
        }
    }
}
