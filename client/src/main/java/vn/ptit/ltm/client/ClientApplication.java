package vn.ptit.ltm.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import vn.ptit.ltm.client.config.ClientConfig;
import vn.ptit.ltm.client.service.AuthClientService;
import vn.ptit.ltm.client.state.ClientSessionState;
import vn.ptit.ltm.client.ui.SceneNavigator;

public final class ClientApplication extends Application {
    private AuthClientService authService;
    private Runnable removeConnectionListener;

    @Override
    public void start(Stage stage) {
        ClientSessionState sessionState = new ClientSessionState();
        authService = new AuthClientService(ClientConfig.fromEnvironment(), sessionState);
        SceneNavigator navigator = new SceneNavigator(stage, authService, sessionState);
        removeConnectionListener = authService.addConnectionStateListener(state ->
                Platform.runLater(() -> navigator.onConnectionStateChanged(state))
        );

        navigator.showLogin(null, null);
        authService.connectAsync();
    }

    @Override
    public void stop() {
        if (removeConnectionListener != null) {
            removeConnectionListener.run();
        }
        if (authService != null) {
            authService.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
