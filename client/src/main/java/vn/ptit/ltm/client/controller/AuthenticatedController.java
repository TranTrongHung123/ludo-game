package vn.ptit.ltm.client.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import vn.ptit.ltm.client.service.AuthClientService;
import vn.ptit.ltm.client.state.ClientSessionState;
import vn.ptit.ltm.client.state.ConnectionState;
import vn.ptit.ltm.client.ui.SceneNavigator;
import vn.ptit.ltm.client.util.UiErrorMessages;
import vn.ptit.ltm.common.dto.player.PlayerProfileDto;

import java.util.Objects;

public final class AuthenticatedController implements ConnectionAwareController {
    @FXML
    private Label welcomeLabel;
    @FXML
    private Label usernameLabel;
    @FXML
    private Label scoreLabel;
    @FXML
    private Label connectionLabel;
    @FXML
    private Label feedbackLabel;
    @FXML
    private Button logoutButton;

    private AuthClientService authService;
    private SceneNavigator navigator;

    public void configure(
            AuthClientService authService,
            ClientSessionState sessionState,
            SceneNavigator navigator
    ) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        PlayerProfileDto profile = Objects.requireNonNull(sessionState, "sessionState")
                .profile()
                .orElseThrow(() -> new IllegalStateException("Authenticated view requires a session"));
        welcomeLabel.setText("Xin chào, " + profile.displayName() + "!");
        usernameLabel.setText("@" + profile.username());
        scoreLabel.setText("Điểm: " + profile.totalScore().stripTrailingZeros().toPlainString()
                + "  •  Hạng nhất: " + profile.firstPlaceCount());
        onConnectionStateChanged(authService.connectionState());
    }

    @FXML
    private void handleLogout() {
        logoutButton.setDisable(true);
        feedbackLabel.setText("Đang đăng xuất...");
        authService.logout().whenComplete((ignored, failure) -> Platform.runLater(() -> {
            logoutButton.setDisable(false);
            if (failure != null) {
                feedbackLabel.setText(UiErrorMessages.from(failure));
                feedbackLabel.getStyleClass().setAll("feedback-error");
                return;
            }
            navigator.showLogin(null, "Đã đăng xuất an toàn.");
        }));
    }

    @Override
    public void onConnectionStateChanged(ConnectionState state) {
        switch (state) {
            case CONNECTED -> connectionLabel.setText("Đã kết nối Game Server");
            case CONNECTING -> connectionLabel.setText("Đang kết nối Game Server...");
            case DISCONNECTED -> connectionLabel.setText("Mất kết nối — session đang được Server giữ tạm thời");
        }
        connectionLabel.getStyleClass().removeAll("connection-online", "connection-offline");
        connectionLabel.getStyleClass().add(
                state == ConnectionState.CONNECTED ? "connection-online" : "connection-offline"
        );
        logoutButton.setDisable(state != ConnectionState.CONNECTED);
    }
}
