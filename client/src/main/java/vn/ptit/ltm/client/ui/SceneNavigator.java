package vn.ptit.ltm.client.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import vn.ptit.ltm.client.controller.AuthenticatedController;
import vn.ptit.ltm.client.controller.ConnectionAwareController;
import vn.ptit.ltm.client.controller.LoginController;
import vn.ptit.ltm.client.controller.RegisterController;
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
    }

    public void showRegister(String username) {
        FXMLLoader loader = loader("/fxml/register.fxml");
        Parent root = load(loader);
        RegisterController controller = loader.getController();
        controller.configure(authService, this, username);
        show(root, controller, "Đăng ký");
    }

    public void showAuthenticated() {
        FXMLLoader loader = loader("/fxml/authenticated.fxml");
        Parent root = load(loader);
        AuthenticatedController controller = loader.getController();
        controller.configure(authService, sessionState, this);
        show(root, controller, "Sảnh chờ");
    }

    public void onConnectionStateChanged(ConnectionState state) {
        if (currentController != null) {
            currentController.onConnectionStateChanged(state);
        }
    }

    private void show(Parent root, ConnectionAwareController controller, String viewTitle) {
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
