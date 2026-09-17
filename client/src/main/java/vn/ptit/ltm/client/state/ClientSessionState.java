package vn.ptit.ltm.client.state;

import vn.ptit.ltm.common.dto.auth.LoginResult;
import vn.ptit.ltm.common.dto.lobby.OnlinePlayersPayload;
import vn.ptit.ltm.common.dto.player.PlayerProfileDto;
import vn.ptit.ltm.common.dto.room.RoomDto;
import vn.ptit.ltm.common.dto.room.InvitationDto;
import vn.ptit.ltm.common.dto.game.GameStateDto;
import vn.ptit.ltm.common.dto.game.DiceResultDto;
import vn.ptit.ltm.common.dto.game.GameOverDto;
import vn.ptit.ltm.common.dto.game.TurnTimeoutDto;
import vn.ptit.ltm.common.dto.session.ReconnectResult;

import java.util.Objects;
import java.util.Optional;

public final class ClientSessionState {
    private String sessionId;
    private PlayerProfileDto profile;
    private OnlinePlayersPayload onlinePlayers = new OnlinePlayersPayload(null);
    private RoomDto room;
    private InvitationDto invitation;
    private GameStateDto gameState;
    private DiceResultDto lastDiceResult;
    private TurnTimeoutDto lastTurnTimeout;
    private GameOverDto gameOver;

    public synchronized void authenticate(LoginResult result) {
        Objects.requireNonNull(result, "result");
        sessionId = result.sessionId();
        profile = result.profile();
    }

    public synchronized Optional<PlayerProfileDto> profile() {
        return Optional.ofNullable(profile);
    }

    public synchronized boolean isAuthenticated() {
        return sessionId != null && profile != null;
    }

    public synchronized String requireSessionId() {
        if (sessionId == null) {
            throw new IllegalStateException("Client is not authenticated");
        }
        return sessionId;
    }

    public synchronized OnlinePlayersPayload onlinePlayers() {
        return onlinePlayers;
    }

    public synchronized void updateOnlinePlayers(OnlinePlayersPayload payload) {
        onlinePlayers = Objects.requireNonNull(payload, "payload");
        if (profile != null) {
            payload.players().stream()
                    .filter(player -> player.playerId().equals(profile.playerId()))
                    .findFirst()
                    .ifPresent(player -> profile = new PlayerProfileDto(
                            profile.playerId(),
                            profile.username(),
                            profile.displayName(),
                            player.totalScore(),
                            player.firstPlaceCount(),
                            profile.totalGames(),
                            profile.wins(),
                            profile.losses()
                    ));
        }
    }

    public synchronized Optional<RoomDto> room() {
        return Optional.ofNullable(room);
    }

    public synchronized void updateRoom(RoomDto updatedRoom) {
        room = Objects.requireNonNull(updatedRoom, "updatedRoom");
    }

    public synchronized void clearRoom() {
        room = null;
        gameState = null;
        lastDiceResult = null;
        lastTurnTimeout = null;
        gameOver = null;
    }

    public synchronized Optional<InvitationDto> invitation() {
        return Optional.ofNullable(invitation);
    }

    public synchronized void updateInvitation(InvitationDto updatedInvitation) {
        invitation = Objects.requireNonNull(updatedInvitation, "updatedInvitation");
    }

    public synchronized void clearInvitation() {
        invitation = null;
    }

    public synchronized Optional<GameStateDto> gameState() {
        return Optional.ofNullable(gameState);
    }

    public synchronized void updateGameState(GameStateDto updatedGameState) {
        Objects.requireNonNull(updatedGameState, "updatedGameState");
        if (gameState != null
                && gameState.matchId().equals(updatedGameState.matchId())
                && updatedGameState.stateVersion() < gameState.stateVersion()) {
            return;
        }
        if (gameState == null || !gameState.matchId().equals(updatedGameState.matchId())) {
            lastDiceResult = null;
            lastTurnTimeout = null;
            gameOver = null;
        }
        gameState = updatedGameState;
    }

    /**
     * Thay snapshot cục bộ bằng full state authoritative nhận được sau reconnect.
     * Các event tạm của connection cũ bị xóa để UI không hiển thị lại dice/timeout cũ.
     */
    public synchronized void restoreAfterReconnect(ReconnectResult result) {
        Objects.requireNonNull(result, "result");
        if (!result.restored()) {
            throw new IllegalArgumentException("Reconnect result was not restored");
        }
        room = result.room();
        GameStateDto restoredGameState = result.gameState();
        if (restoredGameState == null) {
            gameState = null;
        } else if (gameState == null
                || !gameState.matchId().equals(restoredGameState.matchId())
                || restoredGameState.stateVersion() >= gameState.stateVersion()) {
            // Event timeout/move mới hơn có thể đến trước RECONNECT_RESULT do hai thread Server gửi đồng thời.
            gameState = restoredGameState;
        }
        invitation = null;
        lastDiceResult = null;
        lastTurnTimeout = null;
        gameOver = null;
    }

    public synchronized Optional<DiceResultDto> lastDiceResult() {
        return Optional.ofNullable(lastDiceResult);
    }

    public synchronized void updateDiceResult(DiceResultDto diceResult) {
        lastDiceResult = Objects.requireNonNull(diceResult, "diceResult");
    }

    public synchronized Optional<TurnTimeoutDto> lastTurnTimeout() {
        return Optional.ofNullable(lastTurnTimeout);
    }

    public synchronized void updateTurnTimeout(TurnTimeoutDto timeout) {
        lastTurnTimeout = Objects.requireNonNull(timeout, "timeout");
    }

    public synchronized Optional<GameOverDto> gameOver() {
        return Optional.ofNullable(gameOver);
    }

    public synchronized void updateGameOver(GameOverDto result) {
        gameOver = Objects.requireNonNull(result, "result");
    }

    public synchronized void clear() {
        sessionId = null;
        profile = null;
        onlinePlayers = new OnlinePlayersPayload(null);
        room = null;
        invitation = null;
        gameState = null;
        lastDiceResult = null;
        lastTurnTimeout = null;
        gameOver = null;
    }
}
