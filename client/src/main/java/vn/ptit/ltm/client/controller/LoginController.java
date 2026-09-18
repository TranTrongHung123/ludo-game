package vn.ptit.ltm.client.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.transform.Scale;
import vn.ptit.ltm.client.service.AuthClientService;
import vn.ptit.ltm.client.state.ConnectionState;
import vn.ptit.ltm.client.ui.SceneNavigator;
import vn.ptit.ltm.client.util.UiErrorMessages;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.prefs.Preferences;

public final class LoginController implements ConnectionAwareController {
    private static final double DESIGN_WIDTH = 1_672.0;
    private static final double DESIGN_HEIGHT = 912.0;
    private static final String REMEMBERED_USERNAME_KEY = "rememberedUsername";
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(LoginController.class);

    @FXML
    private Pane loginRoot;
    @FXML
    private Group scaledContent;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField visiblePasswordField;
    @FXML
    private Button passwordVisibilityButton;
    @FXML
    private Label connectionLabel;
    @FXML
    private Label feedbackLabel;
    @FXML
    private CheckBox rememberCheckBox;
    @FXML
    private Button loginButton;
    @FXML
    private Button retryButton;

    private AuthClientService authService;
    private SceneNavigator navigator;
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private boolean busy;

    @FXML
    private void initialize() {
        Scale scale = new Scale(1.0, 1.0, 0.0, 0.0);
        scale.xProperty().bind(loginRoot.widthProperty().divide(DESIGN_WIDTH));
        scale.yProperty().bind(loginRoot.heightProperty().divide(DESIGN_HEIGHT));
        scaledContent.getTransforms().setAll(scale);
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
    }

    public void configure(
            AuthClientService authService,
            SceneNavigator navigator,
            String initialUsername,
            String notice
    ) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        String rememberedUsername = PREFERENCES.get(REMEMBERED_USERNAME_KEY, "");
        if (initialUsername != null && !initialUsername.isBlank()) {
            usernameField.setText(initialUsername);
            rememberCheckBox.setSelected(initialUsername.equals(rememberedUsername));
        } else if (!rememberedUsername.isBlank()) {
            usernameField.setText(rememberedUsername);
            rememberCheckBox.setSelected(true);
        }
        if (notice != null && !notice.isBlank()) {
            showFeedback(notice, false);
        }
        onConnectionStateChanged(authService.connectionState());
        Platform.runLater(() -> {
            if (usernameField.getText().isBlank()) {
                usernameField.requestFocus();
            } else {
                passwordField.requestFocus();
            }
        });
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        String validationMessage = validate(username, password);
        if (validationMessage != null) {
            showFeedback(validationMessage, true);
            return;
        }

        setBusy(true);
        showFeedback("Đang đăng nhập...", false);
        authService.login(username, password).whenComplete((result, failure) ->
                Platform.runLater(() -> {
                    passwordField.clear();
                    setBusy(false);
                    if (failure != null) {
                        showFeedback(UiErrorMessages.from(failure), true);
                        return;
                    }
                    rememberUsername(username);
                    navigator.showAuthenticated();
                })
        );
    }

    @FXML
    private void showRegister() {
        navigator.showRegister(usernameField.getText());
    }

    @FXML
    private void showPasswordRecoveryNotice() {
        showFeedback("Chức năng khôi phục mật khẩu chưa được hỗ trợ.", true);
    }

    @FXML
    private void togglePasswordVisibility() {
        boolean showPassword = !visiblePasswordField.isVisible();
        visiblePasswordField.setVisible(showPassword);
        passwordField.setVisible(!showPassword);
        passwordVisibilityButton.setText(showPassword ? "◉" : "");
        passwordVisibilityButton.getStyleClass().remove("password-visible");
        if (showPassword) {
            passwordVisibilityButton.getStyleClass().add("password-visible");
        }
        passwordVisibilityButton.setAccessibleText(showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu");

        TextField activeField = showPassword ? visiblePasswordField : passwordField;
        activeField.requestFocus();
        activeField.positionCaret(activeField.getText().length());
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
            case CONNECTED -> connectionLabel.setText("●  Đã kết nối Game Server");
            case CONNECTING -> connectionLabel.setText("●  Đang kết nối Game Server...");
            case DISCONNECTED -> connectionLabel.setText("●  Mất kết nối Game Server");
        }
        connectionLabel.getStyleClass().removeAll(
                "connection-online",
                "connection-connecting",
                "connection-offline"
        );
        connectionLabel.getStyleClass().add(switch (state) {
            case CONNECTED -> "connection-online";
            case CONNECTING -> "connection-connecting";
            case DISCONNECTED -> "connection-offline";
        });
        connectionLabel.setVisible(state != ConnectionState.CONNECTED);
        retryButton.setVisible(state == ConnectionState.DISCONNECTED);
        retryButton.setManaged(state == ConnectionState.DISCONNECTED);
        refreshSubmitState();
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        refreshSubmitState();
    }

    private void refreshSubmitState() {
        loginButton.setDisable(busy || connectionState != ConnectionState.CONNECTED);
    }

    private void showFeedback(String message, boolean error) {
        feedbackLabel.setText(message);
        feedbackLabel.setVisible(message != null && !message.isBlank());
        feedbackLabel.getStyleClass().removeAll("feedback-error", "feedback-success");
        feedbackLabel.getStyleClass().add(error ? "feedback-error" : "feedback-success");
    }

    private void rememberUsername(String username) {
        if (rememberCheckBox.isSelected()) {
            PREFERENCES.put(REMEMBERED_USERNAME_KEY, username);
        } else {
            PREFERENCES.remove(REMEMBERED_USERNAME_KEY);
        }
    }

    private static String validate(String username, String password) {
        if (username == null || username.isBlank()) {
            return "Vui lòng nhập tên đăng nhập.";
        }
        if (!username.equals(username.trim()) || username.length() > 50) {
            return "Tên đăng nhập không hợp lệ.";
        }
        if (password == null || password.isBlank()) {
            return "Vui lòng nhập mật khẩu.";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return "Mật khẩu không được vượt quá 72 byte UTF-8.";
        }
        return null;
    }
}
