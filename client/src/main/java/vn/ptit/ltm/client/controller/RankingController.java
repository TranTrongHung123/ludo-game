package vn.ptit.ltm.client.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
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
import vn.ptit.ltm.common.dto.ranking.RankingEntryDto;

import java.util.Objects;

public final class RankingController implements ConnectionAwareController {
    @FXML
    private Label connectionLabel;
    @FXML
    private Label feedbackLabel;
    @FXML
    private ListView<RankingEntryDto> rankingList;
    @FXML
    private Button refreshButton;

    private AuthClientService authService;
    private ClientSessionState sessionState;
    private SceneNavigator navigator;
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private boolean loading;

    public void configure(
            AuthClientService authService,
            ClientSessionState sessionState,
            SceneNavigator navigator
    ) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.sessionState = Objects.requireNonNull(sessionState, "sessionState");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        rankingList.setPlaceholder(new Label("Chưa có người chơi trong bảng xếp hạng."));
        rankingList.setCellFactory(ignored -> new RankingCell());
        onConnectionStateChanged(authService.connectionState());
        loadRanking();
    }

    @FXML
    private void handleBack() {
        navigator.showLobby();
    }

    @FXML
    private void handleRefresh() {
        loadRanking();
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

    private void loadRanking() {
        if (connectionState != ConnectionState.CONNECTED || loading) {
            return;
        }
        setLoading(true);
        feedbackLabel.setText("Đang tải bảng xếp hạng từ Server...");
        authService.getRanking().whenComplete((payload, failure) -> Platform.runLater(() -> {
            setLoading(false);
            if (failure != null) {
                showFeedback(UiErrorMessages.from(failure), true);
                return;
            }
            rankingList.getItems().setAll(payload.entries());
            showFeedback("Đã cập nhật " + payload.entries().size() + " người chơi.", false);
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

    private final class RankingCell extends ListCell<RankingEntryDto> {
        @Override
        protected void updateItem(RankingEntryDto entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setGraphic(null);
                setText(null);
                getStyleClass().remove("current-player-row");
                return;
            }
            Label rank = new Label("#" + entry.rank());
            rank.getStyleClass().add(entry.rank() <= 3 ? "ranking-podium" : "ranking-position");
            Label name = new Label(entry.displayName());
            name.getStyleClass().add("ranking-name");
            Label score = new Label(entry.totalScore().stripTrailingZeros().toPlainString() + " điểm");
            score.getStyleClass().add("ranking-score");
            Label wins = new Label(entry.firstPlaceCount() + " lần hạng nhất");
            wins.getStyleClass().add("screen-subtitle");
            VBox details = new VBox(3.0, name, wins);
            HBox.setHgrow(details, Priority.ALWAYS);
            HBox row = new HBox(14.0, rank, details, score);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.getStyleClass().add("data-row");
            setGraphic(row);

            boolean isSelf = sessionState.profile()
                    .map(profile -> profile.playerId().equals(entry.playerId()))
                    .orElse(false);
            if (isSelf && !getStyleClass().contains("current-player-row")) {
                getStyleClass().add("current-player-row");
            } else if (!isSelf) {
                getStyleClass().remove("current-player-row");
            }
        }
    }
}
