package vn.ptit.ltm.server.lobby;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.ptit.ltm.common.dto.lobby.OnlinePlayersPayload;
import vn.ptit.ltm.common.dto.player.PlayerSummaryDto;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.protocol.JsonMessageCodec;
import vn.ptit.ltm.common.protocol.MessageEnvelope;
import vn.ptit.ltm.common.protocol.MessageFactory;
import vn.ptit.ltm.server.network.ClientConnection;
import vn.ptit.ltm.server.network.ConnectionRegistry;
import vn.ptit.ltm.server.session.SessionManager;
import vn.ptit.ltm.server.session.SessionSnapshot;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class LobbyService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LobbyService.class);

    private final SessionManager sessionManager;
    private final ConnectionRegistry connectionRegistry;
    private final MessageFactory messageFactory;

    public LobbyService(SessionManager sessionManager, ConnectionRegistry connectionRegistry) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.connectionRegistry = Objects.requireNonNull(connectionRegistry, "connectionRegistry");
        this.messageFactory = new MessageFactory(new JsonMessageCodec().objectMapper());
    }

    public OnlinePlayersPayload onlinePlayers() {
        List<PlayerSummaryDto> players = sessionManager.snapshots().stream()
                .sorted(Comparator.comparingLong(SessionSnapshot::userId))
                .map(snapshot -> new PlayerSummaryDto(
                        Long.toString(snapshot.userId()),
                        snapshot.displayName(),
                        snapshot.totalScore(),
                        snapshot.firstPlaceCount(),
                        snapshot.presenceState()
                ))
                .toList();
        return new OnlinePlayersPayload(players);
    }

    public void broadcastOnlinePlayers() {
        MessageEnvelope event = messageFactory.event(
                MessageType.ONLINE_PLAYERS_UPDATED,
                onlinePlayers()
        );
        for (ClientConnection connection : connectionRegistry.snapshot()) {
            if (sessionManager.findByConnectionId(connection.id()).isEmpty()) {
                continue;
            }
            try {
                connection.send(event);
            } catch (IOException exception) {
                LOGGER.debug(
                        "Closing connection {} after lobby broadcast failed: {}",
                        connection.id(),
                        exception.getMessage()
                );
                connection.close();
            }
        }
    }
}
