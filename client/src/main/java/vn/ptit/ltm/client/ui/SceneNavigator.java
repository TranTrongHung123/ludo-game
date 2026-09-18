package vn.ptit.ltm.client.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import vn.ptit.ltm.client.controller.ConnectionAwareController;
import vn.ptit.ltm.client.controller.GameResultController;
import vn.ptit.ltm.client.controller.LobbyController;
import vn.ptit.ltm.client.controller.LoginController;
import vn.ptit.ltm.client.controller.MatchHistoryController;
import vn.ptit.ltm.client.controller.RankingController;
import vn.ptit.ltm.client.controller.RegisterController;
import vn.ptit.ltm.client.controller.RoomController;
import vn.ptit.ltm.client.controller.GameController;
import vn.ptit.ltm.client.service.AuthClientService;
import vn.ptit.ltm.client.state.ClientSessionState;
import vn.ptit.ltm.client.state.ConnectionState;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

public final class SceneNavigator {
    private static final String STYLESHEET = "/css/application.css";

    private final Stage stage;
    private final AuthClientService authService;
    private final ClientSessionState sessionState;
    private ConnectionAwareController currentController;

    public SceneNavigator(
            Stage stage,
            AuthClientService authService,
            ClientSessionState sessionState
    ) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.authService = Objects.requireNonNull(authService, "authService");
        this.sessionState = Objects.requireNonNull(sessionState, "sessionState");
    }

    public void showLogin(String username, String notice) {
        FXMLLoader loader = loader("/fxml/login.fxml");
        Parent root = load(loader);
        LoginController controller = loader.getController();
        controller.configure(authService, this, username, notice);
        show(root, controller, "Đăng nhập");
        ensureAuthenticationWindowSize();
    }

    public void showRegister(String username) {
        FXMLLoader loader = loader("/fxml/register.fxml");
        Parent root = load(loader);
        RegisterController controller = loader.getController();
        controller.configure(authService, this, username);
        show(root, controller, "Đăng ký");
    }

    public void showAuthenticated() {
        showLobby();
    }

    public void showLobby() {
        FXMLLoader loader = loader("/fxml/lobby.fxml");
        Parent root = load(loader);
        LobbyController controller = loader.getController();
        controller.configure(authService, sessionState, this);
        show(root, controller, "Sảnh chờ");
    }

    public void showRoom() {
        FXMLLoader loader = loader("/fxml/room.fxml");
        Parent root = load(loader);
        RoomController controller = loader.getController();
        controller.configure(authService, sessionState, this);
        show(root, controller, "Phòng chờ");
    }

    public void showGame() {
        FXMLLoader loader = loader("/fxml/game.fxml");
        Parent root = load(loader);
        GameController controller = loader.getController();
        controller.configure(authService, sessionState, this);
        show(root, controller, "Trận đấu");
        ensureGameWindowSize();
    }

    public void showRanking() {
        FXMLLoader loader = loader("/fxml/ranking.fxml");
        Parent root = load(loader);
        RankingController controller = loader.getController();
        controller.configure(authService, sessionState, this);
        show(root, controller, "Bảng xếp hạng");
    }

    public void showMatchHistory() {
        FXMLLoader loader = loader("/fxml/match-history.fxml");
        Parent root = load(loader);
        MatchHistoryController controller = loader.getController();
        controller.configure(authService, sessionState, this);
        show(root, controller, "Lịch sử trận đấu");
    }

    public void showGameResult() {
        FXMLLoader loader = loader("/fxml/game-result.fxml");
        Parent root = load(loader);
        GameResultController controller = loader.getController();
        controller.configure(authService, sessionState, this);
        show(root, controller, "Kết quả trận đấu");
    }

    public void onConnectionStateChanged(ConnectionState state) {
        if (currentController != null) {
            currentController.onConnectionStateChanged(state);
        }
    }

    private void show(Parent root, ConnectionAwareController controller, String viewTitle) {
        if (currentController != null) {
            currentController.dispose();
        }
        currentController = controller;
        Scene scene = stage.getScene();
        if (scene == null) {
            scene = new Scene(root, 960, 620);
            URL stylesheet = SceneNavigator.class.getResource(STYLESHEET);
            if (stylesheet != null) {
                scene.getStylesheets().add(stylesheet.toExternalForm());
            }
            stage.setScene(scene);
        } else {
            scene.setRoot(root);
        }
        stage.setTitle("Ludo Game • " + viewTitle);
        stage.setMinWidth(760);
        stage.setMinHeight(540);
        stage.show();
        controller.onConnectionStateChanged(authService.connectionState());
    }

    /** Mở đủ không gian cho bàn cờ và sidebar nhưng vẫn cho phép ScrollPane co trên màn hình nhỏ. */
    private void ensureGameWindowSize() {
        stage.setMinWidth(960.0);
        stage.setMinHeight(640.0);
        if (!stage.isMaximized()) {
            stage.setWidth(Math.max(stage.getWidth(), 1_080.0));
            stage.setHeight(Math.max(stage.getHeight(), 720.0));
            stage.centerOnScreen();
        }
    }

    private void ensureAuthenticationWindowSize() {
        stage.setMinWidth(960.0);
        stage.setMinHeight(600.0);
        stage.setMaximized(true);
    }

    private static FXMLLoader loader(String resourcePath) {
        URL resource = SceneNavigator.class.getResource(resourcePath);
        if (resource == null) {
            throw new IllegalStateException("Missing FXML resource: " + resourcePath);
        }
        return new FXMLLoader(resource);
    }

    private static Parent load(FXMLLoader loader) {
        try {
            return loader.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load JavaFX view", exception);
        }
    }
}
