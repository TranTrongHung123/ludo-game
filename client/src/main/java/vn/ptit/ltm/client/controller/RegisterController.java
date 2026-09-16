package vn.ptit.ltm.client.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import vn.ptit.ltm.client.service.AuthClientService;
import vn.ptit.ltm.client.state.ConnectionState;
import vn.ptit.ltm.client.ui.SceneNavigator;
import vn.ptit.ltm.client.util.UiErrorMessages;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class RegisterController implements ConnectionAwareController {
    @FXML
    private TextField usernameField;
    @FXML
    private TextField displayNameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Label connectionLabel;
    @FXML
    private Label feedbackLabel;
    @FXML
    private Button registerButton;
    @FXML
    private Button retryButton;

    private AuthClientService authService;
    private SceneNavigator navigator;
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private boolean busy;

    public void configure(AuthClientService authService, SceneNavigator navigator, String initialUsername) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        if (initialUsername != null) {
            usernameField.setText(initialUsername);
        }
        onConnectionStateChanged(authService.connectionState());
        Platform.runLater(() -> {
            if (usernameField.getText().isBlank()) {
                usernameField.requestFocus();
            } else {
                displayNameField.requestFocus();
            }
        });
    }

    @FXML
    private void handleRegister() {
        String username = usernameField.getText();
        String displayName = displayNameField.getText();
        String password = passwordField.getText();
        String confirmation = confirmPasswordField.getText();
        String validationMessage = validate(username, displayName, password, confirmation);
        if (validationMessage != null) {
            showFeedback(validationMessage, true);
            return;
        }

        setBusy(true);
        showFeedback("Đang tạo tài khoản...", false);
        authService.register(username, password, displayName).whenComplete((result, failure) ->
                Platform.runLater(() -> {
                    passwordField.clear();
                    confirmPasswordField.clear();
                    setBusy(false);
                    if (failure != null) {
                        showFeedback(UiErrorMessages.from(failure), true);
                        return;
                    }
                    navigator.showLogin(username, "Đăng ký thành công. Bạn có thể đăng nhập ngay.");
                })
        );
    }

    @FXML
    private void showLogin() {
        navigator.showLogin(usernameField.getText(), null);
    }

    @FXML
    private void retryConnection() {
        showFeedback("Đang kết nối lại...", false);
        authService.connectAsync().exceptionally(failure -> {
            Platform.runLater(() -> showFeedback(UiErrorMessages.from(failure), true));
            return null;
        });
    }

    @Override
    public void onConnectionStateChanged(ConnectionState state) {
        connectionState = state;
        switch (state) {
            case CONNECTED -> connectionLabel.setText("Đã kết nối Game Server");
            case CONNECTING -> connectionLabel.setText("Đang kết nối Game Server...");
            case DISCONNECTED -> connectionLabel.setText("Mất kết nối Game Server");
        }
        connectionLabel.getStyleClass().removeAll("connection-online", "connection-offline");
        connectionLabel.getStyleClass().add(
                state == ConnectionState.CONNECTED ? "connection-online" : "connection-offline"
        );
        retryButton.setVisible(state == ConnectionState.DISCONNECTED);
        retryButton.setManaged(state == ConnectionState.DISCONNECTED);
        refreshSubmitState();
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        refreshSubmitState();
    }

    private void refreshSubmitState() {
        registerButton.setDisable(busy || connectionState != ConnectionState.CONNECTED);
    }

    private void showFeedback(String message, boolean error) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().removeAll("feedback-error", "feedback-success");
        feedbackLabel.getStyleClass().add(error ? "feedback-error" : "feedback-success");
    }

    private static String validate(
            String username,
            String displayName,
            String password,
            String confirmation
    ) {
        if (username == null || username.isBlank()) {
            return "Vui lòng nhập tên đăng nhập.";
        }
        if (!username.equals(username.trim()) || username.length() > 50) {
            return "Tên đăng nhập không hợp lệ.";
        }
        if (displayName == null || displayName.isBlank()) {
            return "Vui lòng nhập tên hiển thị.";
        }
        if (!displayName.equals(displayName.trim()) || displayName.length() > 100) {
            return "Tên hiển thị không hợp lệ.";
        }
        if (password == null || password.isBlank()) {
            return "Vui lòng nhập mật khẩu.";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return "Mật khẩu không được vượt quá 72 byte UTF-8.";
        }
        if (!password.equals(confirmation)) {
            return "Mật khẩu xác nhận không khớp.";
        }
        return null;
    }
}
