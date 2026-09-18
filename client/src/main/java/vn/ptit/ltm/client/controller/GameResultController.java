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
import vn.ptit.ltm.common.dto.game.GameOverDto;
import vn.ptit.ltm.common.dto.game.MatchStandingDto;
import vn.ptit.ltm.common.enums.MatchParticipantStatus;

import java.util.Comparator;
import java.util.Objects;

public final class GameResultController implements ConnectionAwareController {
    @FXML
    private Label matchIdLabel;
    @FXML
    private Label headlineLabel;
    @FXML
    private Label connectionLabel;
    @FXML
    private Label feedbackLabel;
    @FXML
    private ListView<MatchStandingDto> standingsList;
    @FXML
    private Button lobbyButton;

    private AuthClientService authService;
    private ClientSessionState sessionState;
    private SceneNavigator navigator;
    private GameOverDto result;
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private boolean leaving;

    public void configure(
            AuthClientService authService,
            ClientSessionState sessionState,
            SceneNavigator navigator
    ) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.sessionState = Objects.requireNonNull(sessionState, "sessionState");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        result = sessionState.gameOver()
                .orElseThrow(() -> new IllegalStateException("Game result view requires GAME_OVER data"));
        standingsList.setCellFactory(ignored -> new StandingCell());
        standingsList.getItems().setAll(result.standings().stream()
                .sorted(Comparator.comparingInt(MatchStandingDto::rank))
                .toList());
        matchIdLabel.setText(result.matchId());
        String selfId = sessionState.profile().map(profile -> profile.playerId()).orElse("");
        result.standings().stream()
                .filter(standing -> standing.playerId().equals(selfId))
                .findFirst()
                .ifPresentOrElse(
                        standing -> headlineLabel.setText(standing.matchStatus() == MatchParticipantStatus.FORFEITED
                                ? "Bạn đã bỏ cuộc • Hạng " + standing.rank()
                                : "Bạn về hạng " + standing.rank() + "!"),
                        () -> headlineLabel.setText("Trận đấu đã kết thúc")
                );
        onConnectionStateChanged(authService.connectionState());
    }

    @FXML
    private void handleReturnToLobby() {
        if (leaving) {
            return;
        }
        if (sessionState.room().isEmpty()) {
            navigator.showLobby();
            return;
        }
        leaving = true;
        refreshAction();
        showFeedback("Đang rời phòng và trở về sảnh...", false);
        authService.leaveRoom(result.roomId()).whenComplete((ignored, failure) -> Platform.runLater(() -> {
            leaving = false;
            refreshAction();
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
        connectionLabel.setText(switch (state) {
            case CONNECTED -> "Kết quả đã đồng bộ với Server";
            case CONNECTING -> "Đang kết nối lại...";
            case DISCONNECTED -> "Mất kết nối — chưa thể rời phòng";
        });
        connectionLabel.getStyleClass().removeAll("connection-online", "connection-offline");
        connectionLabel.getStyleClass().add(
                state == ConnectionState.CONNECTED ? "connection-online" : "connection-offline"
        );
        refreshAction();
    }

    private void refreshAction() {
        lobbyButton.setDisable(leaving || connectionState != ConnectionState.CONNECTED);
    }

    private void showFeedback(String text, boolean error) {
        feedbackLabel.setText(text);
        feedbackLabel.getStyleClass().setAll(error ? "feedback-error" : "feedback-success");
    }

    private final class StandingCell extends ListCell<MatchStandingDto> {
        @Override
        protected void updateItem(MatchStandingDto standing, boolean empty) {
            super.updateItem(standing, empty);
            if (empty || standing == null) {
                setGraphic(null);
                setText(null);
                getStyleClass().remove("current-player-row");
                return;
            }
            Label rank = new Label("#" + standing.rank());
            rank.getStyleClass().add(standing.rank() <= 3 ? "ranking-podium" : "ranking-position");
            Label name = new Label(standing.displayName());
            name.getStyleClass().add("ranking-name");
            Label metadata = new Label(UiFormatters.color(standing.color()) + " • "
                    + UiFormatters.participantStatus(standing.matchStatus()));
            metadata.getStyleClass().add(standing.matchStatus() == MatchParticipantStatus.FORFEITED
                    ? "result-forfeit" : "screen-subtitle");
            Label earned = new Label(UiFormatters.score(standing.scoreEarned()) + " điểm");
            earned.getStyleClass().add(standing.matchStatus() == MatchParticipantStatus.FORFEITED
                    ? "result-forfeit" : "ranking-score");
            Label total = new Label("Tổng: " + standing.totalScore().stripTrailingZeros().toPlainString());
            total.getStyleClass().add("screen-subtitle");
            VBox details = new VBox(3.0, name, metadata);
            HBox.setHgrow(details, Priority.ALWAYS);
            VBox scores = new VBox(3.0, earned, total);
            scores.setAlignment(Pos.CENTER_RIGHT);
            HBox row = new HBox(14.0, rank, details, scores);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("data-row");
            setGraphic(row);

            boolean self = sessionState.profile()
                    .map(profile -> profile.playerId().equals(standing.playerId()))
                    .orElse(false);
            if (self && !getStyleClass().contains("current-player-row")) {
                getStyleClass().add("current-player-row");
            } else if (!self) {
                getStyleClass().remove("current-player-row");
            }
        }
    }
}
