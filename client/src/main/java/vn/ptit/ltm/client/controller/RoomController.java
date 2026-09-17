package vn.ptit.ltm.client.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import vn.ptit.ltm.client.service.AuthClientService;
import vn.ptit.ltm.client.state.ClientSessionState;
import vn.ptit.ltm.client.state.ConnectionState;
import vn.ptit.ltm.client.ui.SceneNavigator;
import vn.ptit.ltm.client.util.UiErrorMessages;
import vn.ptit.ltm.common.dto.room.RoomDto;
import vn.ptit.ltm.common.dto.room.RoomPlayerDto;
import vn.ptit.ltm.common.dto.player.PlayerSummaryDto;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.enums.PlayerPresenceState;
import vn.ptit.ltm.common.enums.RoomState;

import java.util.Objects;

public final class RoomController implements ConnectionAwareController {
    @FXML
    private Label roomIdLabel;
    @FXML
    private Label hostLabel;
    @FXML
    private Label stateLabel;
    @FXML
    private Label connectionLabel;
    @FXML
    private Label feedbackLabel;
    @FXML
    private ListView<String> roomPlayersList;
    @FXML
    private Button leaveRoomButton;
    @FXML
    private Button readyButton;
    @FXML
    private Button startGameButton;
    @FXML
    private Button inviteButton;
    @FXML
    private ListView<PlayerSummaryDto> invitablePlayersList;

    private AuthClientService authService;
    private ClientSessionState sessionState;
    private SceneNavigator navigator;
    private Runnable removeRoomListener;
    private Runnable removeOnlinePlayersListener;
    private Runnable removeGameStateListener;
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private boolean busy;
    private boolean gameViewOpened;

    public void configure(
            AuthClientService authService,
            ClientSessionState sessionState,
            SceneNavigator navigator
    ) {
        this.authService = Objects.requireNonNull(authService, "authService");
        this.sessionState = Objects.requireNonNull(sessionState, "sessionState");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        sessionState.room().orElseThrow(() -> new IllegalStateException("Room view requires a room"));
        removeRoomListener = authService.addStateListener(
                MessageType.ROOM_UPDATED,
                () -> Platform.runLater(this::renderRoom)
        );
        removeOnlinePlayersListener = authService.addStateListener(
                MessageType.ONLINE_PLAYERS_UPDATED,
                () -> Platform.runLater(this::renderInvitablePlayers)
        );
        removeGameStateListener = authService.addStateListener(
                MessageType.GAME_STATE,
                () -> Platform.runLater(this::openGameOnce)
        );
        invitablePlayersList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(PlayerSummaryDto player, boolean empty) {
                super.updateItem(player, empty);
                setText(empty || player == null
                        ? null
                        : player.displayName() + " • " + player.totalScore().stripTrailingZeros().toPlainString()
                        + " điểm");
            }
        });
        invitablePlayersList.getSelectionModel().selectedItemProperty()
                .addListener((ignored, previous, selected) -> refreshActionState());
        renderRoom();
        renderInvitablePlayers();
        onConnectionStateChanged(authService.connectionState());
        authService.getOnlinePlayers().exceptionally(failure -> {
            Platform.runLater(() -> showFailure(failure));
            return null;
        });
    }

    @FXML
    private void handleLeaveRoom() {
        RoomDto room = sessionState.room().orElse(null);
        if (room == null) {
            navigator.showLobby();
            return;
        }
        setBusy(true);
        feedbackLabel.setText("Đang rời phòng...");
        authService.leaveRoom(room.roomId()).whenComplete((ignored, failure) -> Platform.runLater(() -> {
            setBusy(false);
            if (failure != null) {
                feedbackLabel.setText(UiErrorMessages.from(failure));
                feedbackLabel.getStyleClass().setAll("feedback-error");
                return;
            }
            navigator.showLobby();
        }));
    }

    @FXML
    private void handleToggleReady() {
        RoomDto room = sessionState.room().orElse(null);
        RoomPlayerDto self = currentPlayer(room);
        if (room == null || self == null) {
            return;
        }
        boolean nextReady = !self.ready();
        setBusy(true);
        feedbackLabel.setText(nextReady ? "Đang xác nhận sẵn sàng..." : "Đang hủy sẵn sàng...");
        authService.setReady(room.roomId(), nextReady)
                .whenComplete((payload, failure) -> Platform.runLater(() -> {
                    setBusy(false);
                    if (failure != null) {
                        showFailure(failure);
                        return;
                    }
                    feedbackLabel.setText(nextReady ? "Bạn đã sẵn sàng." : "Bạn chưa sẵn sàng.");
                    feedbackLabel.getStyleClass().setAll("feedback-success");
                    renderRoom();
                }));
    }

    @FXML
    private void handleInvitePlayer() {
        RoomDto room = sessionState.room().orElse(null);
        PlayerSummaryDto target = invitablePlayersList.getSelectionModel().getSelectedItem();
        if (room == null || target == null) {
            feedbackLabel.setText("Hãy chọn một người chơi đang rỗi để mời.");
            feedbackLabel.getStyleClass().setAll("feedback-error");
            return;
        }
        setBusy(true);
        feedbackLabel.setText("Đang gửi lời mời...");
        authService.invitePlayer(room.roomId(), target.playerId())
                .whenComplete((invitation, failure) -> Platform.runLater(() -> {
                    setBusy(false);
                    if (failure != null) {
                        showFailure(failure);
                        return;
                    }
                    feedbackLabel.setText("Đã mời " + target.displayName() + ".");
                    feedbackLabel.getStyleClass().setAll("feedback-success");
                }));
    }

    @FXML
    private void handleStartGame() {
        RoomDto room = sessionState.room().orElse(null);
        if (room == null) {
            return;
        }
        setBusy(true);
        feedbackLabel.setText("Đang bắt đầu trận...");
        authService.startGame(room.roomId())
                .whenComplete((gameState, failure) -> Platform.runLater(() -> {
                    setBusy(false);
                    if (failure != null) {
                        showFailure(failure);
                        return;
                    }
                    openGameOnce();
                }));
    }

    private void renderRoom() {
        RoomDto room = sessionState.room().orElse(null);
        if (room == null) {
            return;
        }
        roomIdLabel.setText(room.roomId());
        stateLabel.setText("Trạng thái: " + room.state());
        String hostName = room.players().stream()
                .filter(player -> player.playerId().equals(room.hostPlayerId()))
                .map(RoomPlayerDto::displayName)
                .findFirst()
                .orElse(room.hostPlayerId());
        hostLabel.setText("Chủ phòng: " + hostName);
        roomPlayersList.getItems().setAll(room.players().stream()
                .map(player -> formatPlayer(player, room.hostPlayerId()))
                .toList());
        RoomPlayerDto self = currentPlayer(room);
        boolean waiting = room.state() == RoomState.WAITING;
        boolean isHost = self != null && self.playerId().equals(room.hostPlayerId());
        readyButton.setVisible(waiting);
        readyButton.setManaged(waiting);
        readyButton.setText(self != null && self.ready() ? "Hủy sẵn sàng" : "Sẵn sàng");
        startGameButton.setVisible(waiting && isHost);
        startGameButton.setManaged(waiting && isHost);
        inviteButton.setVisible(waiting && isHost);
        inviteButton.setManaged(waiting && isHost);
        invitablePlayersList.setVisible(waiting && isHost);
        invitablePlayersList.setManaged(waiting && isHost);
        leaveRoomButton.setVisible(waiting);
        leaveRoomButton.setManaged(waiting);
        refreshActionState();
    }

    private void renderInvitablePlayers() {
        String selfId = sessionState.profile().map(profile -> profile.playerId()).orElse("");
        invitablePlayersList.getItems().setAll(sessionState.onlinePlayers().players().stream()
                .filter(player -> !player.playerId().equals(selfId))
                .filter(player -> player.presenceState() == PlayerPresenceState.IDLE)
                .toList());
        refreshActionState();
    }

    @Override
    public void onConnectionStateChanged(ConnectionState state) {
        connectionState = state;
        switch (state) {
            case CONNECTED -> connectionLabel.setText("Đã kết nối Game Server");
            case CONNECTING -> connectionLabel.setText("Đang kết nối Game Server...");
            case DISCONNECTED -> connectionLabel.setText("Mất kết nối — bạn vẫn thuộc phòng trong grace period");
        }
        connectionLabel.getStyleClass().removeAll("connection-online", "connection-offline");
        connectionLabel.getStyleClass().add(
                state == ConnectionState.CONNECTED ? "connection-online" : "connection-offline"
        );
        refreshActionState();
    }

    @Override
    public void dispose() {
        if (removeRoomListener != null) {
            removeRoomListener.run();
            removeRoomListener = null;
        }
        if (removeOnlinePlayersListener != null) {
            removeOnlinePlayersListener.run();
            removeOnlinePlayersListener = null;
        }
        if (removeGameStateListener != null) {
            removeGameStateListener.run();
            removeGameStateListener = null;
        }
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        refreshActionState();
    }

    private void refreshActionState() {
        boolean disabled = busy || connectionState != ConnectionState.CONNECTED;
        RoomDto room = sessionState == null ? null : sessionState.room().orElse(null);
        RoomPlayerDto self = currentPlayer(room);
        boolean canStart = room != null
                && self != null
                && self.playerId().equals(room.hostPlayerId())
                && room.players().size() >= 2
                && room.players().stream().allMatch(RoomPlayerDto::ready)
                && room.players().stream().allMatch(
                        player -> player.presenceState() == PlayerPresenceState.IN_ROOM
                );
        leaveRoomButton.setDisable(disabled);
        readyButton.setDisable(disabled);
        inviteButton.setDisable(disabled || invitablePlayersList.getSelectionModel().getSelectedItem() == null);
        startGameButton.setDisable(disabled || !canStart);
    }

    private RoomPlayerDto currentPlayer(RoomDto room) {
        if (room == null) {
            return null;
        }
        String playerId = sessionState.profile().map(profile -> profile.playerId()).orElse("");
        return room.players().stream()
                .filter(player -> player.playerId().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    private void openGameOnce() {
        if (gameViewOpened || sessionState.gameState().isEmpty()) {
            return;
        }
        gameViewOpened = true;
        navigator.showGame();
    }

    private void showFailure(Throwable failure) {
        feedbackLabel.setText(UiErrorMessages.from(failure));
        feedbackLabel.getStyleClass().setAll("feedback-error");
    }

    private static String formatPlayer(RoomPlayerDto player, String hostPlayerId) {
        String host = player.playerId().equals(hostPlayerId) ? " • Chủ phòng" : "";
        return "Slot " + player.slotIndex()
                + " • " + player.color()
                + " • " + player.displayName()
                + host
                + " • " + player.presenceState()
                + (player.ready() ? " • Sẵn sàng" : " • Chưa sẵn sàng");
    }
}
