package vn.ptit.ltm.client.controller;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import vn.ptit.ltm.client.service.AuthClientService;
import vn.ptit.ltm.client.network.ClientRequestException;
import vn.ptit.ltm.client.state.ClientSessionState;
import vn.ptit.ltm.client.state.ConnectionState;
import vn.ptit.ltm.client.ui.SceneNavigator;
import vn.ptit.ltm.client.util.UiErrorMessages;
import vn.ptit.ltm.common.dto.player.PlayerProfileDto;
import vn.ptit.ltm.common.dto.player.PlayerSummaryDto;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.enums.PlayerPresenceState;
import vn.ptit.ltm.common.error.ErrorCode;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class LobbyController implements ConnectionAwareController {
    private static final DateTimeFormatter INVITATION_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    @FXML
    private Label welcomeLabel;
    @FXML
    private Label scoreLabel;
    @FXML
    private Label connectionLabel;
    @FXML
    private Label feedbackLabel;
    @FXML
    private ListView<String> onlinePlayersList;
    @FXML
    private TextField roomIdField;
    @FXML
    private Button createRoomButton;
    @FXML
    private Button joinRoomButton;
    @FXML
    private Button logoutButton;
    @FXML
    private VBox invitationBox;
    @FXML
    private Label invitationLabel;
    @FXML
    private Button acceptInvitationButton;
    @FXML
    private Button rejectInvitationButton;

    private AuthClientService authService;
    private ClientSessionState sessionState;
    private SceneNavigator navigator;
    private Runnable removeOnlinePlayersListener;
    private Runnable removeInvitationListener;
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private PauseTransition invitationExpiryTimer;
    private boolean busy;

    public void configure(
            AuthClientService authService,
            ClientSessionState sessionState,
            SceneNavigator navigator
    ) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.sessionState = Objects.requireNonNull(sessionState, "sessionState");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        PlayerProfileDto profile = sessionState.profile()
                .orElseThrow(() -> new IllegalStateException("Lobby requires an authenticated session"));
        welcomeLabel.setText("Xin chào, " + profile.displayName() + "!");
        scoreLabel.setText("Điểm " + profile.totalScore().stripTrailingZeros().toPlainString()
                + "  •  Hạng nhất " + profile.firstPlaceCount());
        removeOnlinePlayersListener = authService.addStateListener(
                MessageType.ONLINE_PLAYERS_UPDATED,
                () -> Platform.runLater(this::renderOnlinePlayers)
        );
        removeInvitationListener = authService.addStateListener(
                MessageType.INVITE_PLAYER,
                () -> Platform.runLater(this::renderInvitation)
        );
        renderOnlinePlayers();
        renderInvitation();
        onConnectionStateChanged(authService.connectionState());
        refreshOnlinePlayers();
    }

    @FXML
    private void handleCreateRoom() {
        setBusy(true);
        showFeedback("Đang tạo phòng...", false);
        authService.createRoom().whenComplete((payload, failure) -> Platform.runLater(() -> {
            setBusy(false);
            if (failure != null) {
                showFeedback(UiErrorMessages.from(failure), true);
                return;
            }
            navigator.showRoom();
        }));
    }

    @FXML
    private void handleJoinRoom() {
        String roomId = roomIdField.getText() == null ? "" : roomIdField.getText().trim();
        if (roomId.isEmpty()) {
            showFeedback("Vui lòng nhập mã phòng.", true);
            return;
        }
        setBusy(true);
        showFeedback("Đang vào phòng...", false);
        authService.joinRoom(roomId).whenComplete((payload, failure) -> Platform.runLater(() -> {
            setBusy(false);
            if (failure != null) {
                showFeedback(UiErrorMessages.from(failure), true);
                return;
            }
            navigator.showRoom();
        }));
    }

    @FXML
    private void handleRefresh() {
        refreshOnlinePlayers();
    }

    @FXML
    private void handleShowRanking() {
        navigator.showRanking();
    }

    @FXML
    private void handleShowMatchHistory() {
        navigator.showMatchHistory();
    }

    @FXML
    private void handleAcceptInvitation() {
        var invitation = sessionState.invitation().orElse(null);
        if (invitation == null) {
            renderInvitation();
            return;
        }
        setBusy(true);
        showFeedback("Đang tham gia phòng được mời...", false);
        authService.acceptInvitation(invitation.invitationId())
                .whenComplete((payload, failure) -> Platform.runLater(() -> {
                    setBusy(false);
                    if (failure != null) {
                        clearTerminalInvitationFailure(failure);
                        showFeedback(UiErrorMessages.from(failure), true);
                        return;
                    }
                    navigator.showRoom();
                }));
    }

    @FXML
    private void handleRejectInvitation() {
        var invitation = sessionState.invitation().orElse(null);
        if (invitation == null) {
            renderInvitation();
            return;
        }
        setBusy(true);
        authService.rejectInvitation(invitation.invitationId())
                .whenComplete((ignored, failure) -> Platform.runLater(() -> {
                    setBusy(false);
                    if (failure != null) {
                        clearTerminalInvitationFailure(failure);
                        showFeedback(UiErrorMessages.from(failure), true);
                        return;
                    }
                    showFeedback("Đã từ chối lời mời.", false);
                    renderInvitation();
                }));
    }

    @FXML
    private void handleLogout() {
        setBusy(true);
        showFeedback("Đang đăng xuất...", false);
        authService.logout().whenComplete((ignored, failure) -> Platform.runLater(() -> {
            setBusy(false);
            if (failure != null) {
                showFeedback(UiErrorMessages.from(failure), true);
                return;
            }
            navigator.showLogin(null, "Đã đăng xuất an toàn.");
        }));
    }

    private void refreshOnlinePlayers() {
        if (connectionState != ConnectionState.CONNECTED) {
            return;
        }
        authService.getOnlinePlayers().exceptionally(failure -> {
            Platform.runLater(() -> showFeedback(UiErrorMessages.from(failure), true));
            return null;
        });
    }

    private void renderOnlinePlayers() {
        onlinePlayersList.getItems().setAll(
                sessionState.onlinePlayers().players().stream()
                        .map(LobbyController::formatPlayer)
                        .toList()
        );
    }

    private void renderInvitation() {
        stopInvitationExpiryTimer();
        var invitation = sessionState.invitation().orElse(null);
        if (invitation != null && invitation.expiresAtEpochMillis() <= System.currentTimeMillis()) {
            sessionState.clearInvitation();
            invitation = null;
        }
        boolean visible = invitation != null;
        invitationBox.setVisible(visible);
        invitationBox.setManaged(visible);
        if (invitation != null) {
            invitationLabel.setText(
                    invitation.inviterDisplayName() + " mời bạn vào phòng " + invitation.roomId()
                            + ". Hết hạn lúc " + INVITATION_TIME_FORMAT.format(
                            Instant.ofEpochMilli(invitation.expiresAtEpochMillis())
                    ) + "."
            );
            String invitationId = invitation.invitationId();
            invitationExpiryTimer = new PauseTransition(javafx.util.Duration.millis(
                    Math.max(1L, invitation.expiresAtEpochMillis() - System.currentTimeMillis())
            ));
            invitationExpiryTimer.setOnFinished(ignored -> sessionState.invitation()
                    .filter(current -> current.invitationId().equals(invitationId))
                    .ifPresent(current -> {
                        sessionState.clearInvitation();
                        showFeedback("Lời mời đã hết hạn.", true);
                        renderInvitation();
                    }));
            invitationExpiryTimer.play();
        }
        refreshActionState();
    }

    @Override
    public void onConnectionStateChanged(ConnectionState state) {
        connectionState = state;
        switch (state) {
            case CONNECTED -> connectionLabel.setText("Đã kết nối Game Server");
            case CONNECTING -> connectionLabel.setText("Đang kết nối Game Server...");
            case DISCONNECTED -> connectionLabel.setText("Mất kết nối — session đang được giữ tạm thời");
        }
        connectionLabel.getStyleClass().removeAll("connection-online", "connection-offline");
        connectionLabel.getStyleClass().add(
                state == ConnectionState.CONNECTED ? "connection-online" : "connection-offline"
        );
        refreshActionState();
    }

    @Override
    public void dispose() {
        if (removeOnlinePlayersListener != null) {
            removeOnlinePlayersListener.run();
            removeOnlinePlayersListener = null;
        }
        if (removeInvitationListener != null) {
            removeInvitationListener.run();
            removeInvitationListener = null;
        }
        stopInvitationExpiryTimer();
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        refreshActionState();
    }

    private void refreshActionState() {
        boolean disabled = busy || connectionState != ConnectionState.CONNECTED;
        createRoomButton.setDisable(disabled);
        joinRoomButton.setDisable(disabled);
        logoutButton.setDisable(disabled);
        boolean invitationUnavailable = sessionState == null || sessionState.invitation().isEmpty();
        acceptInvitationButton.setDisable(disabled || invitationUnavailable);
        rejectInvitationButton.setDisable(disabled || invitationUnavailable);
    }

    private void showFeedback(String message, boolean error) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().removeAll("feedback-error", "feedback-success");
        feedbackLabel.getStyleClass().add(error ? "feedback-error" : "feedback-success");
    }

    private void clearTerminalInvitationFailure(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof ClientRequestException requestException
                && (requestException.errorCode() == ErrorCode.INVITATION_NOT_FOUND
                || requestException.errorCode() == ErrorCode.INVITATION_EXPIRED)) {
            sessionState.clearInvitation();
            renderInvitation();
        }
    }

    private void stopInvitationExpiryTimer() {
        if (invitationExpiryTimer != null) {
            invitationExpiryTimer.stop();
            invitationExpiryTimer = null;
        }
    }

    private static String formatPlayer(PlayerSummaryDto player) {
        return player.displayName()
                + "  •  " + presenceText(player.presenceState())
                + "  •  " + player.totalScore().stripTrailingZeros().toPlainString() + " điểm"
                + "  •  " + player.firstPlaceCount() + " lần hạng nhất";
    }

    private static String presenceText(PlayerPresenceState state) {
        return switch (state) {
            case IDLE -> "Đang rỗi";
            case IN_ROOM -> "Trong phòng";
            case PLAYING -> "Đang chơi";
            case SPECTATING -> "Đang xem";
            case DISCONNECTED -> "Mất kết nối";
            case OFFLINE -> "Ngoại tuyến";
        };
    }
}
