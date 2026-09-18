package vn.ptit.ltm.server.network;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.ptit.ltm.common.dto.auth.RegisterRequest;
import vn.ptit.ltm.common.dto.auth.RegisterResult;
import vn.ptit.ltm.common.dto.player.PlayerProfileDto;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.error.ErrorCode;
import vn.ptit.ltm.common.protocol.JsonMessageCodec;
import vn.ptit.ltm.common.protocol.MessageEnvelope;
import vn.ptit.ltm.common.protocol.MessageFactory;
import vn.ptit.ltm.server.service.AuthService;
import vn.ptit.ltm.server.session.SessionManager;
import vn.ptit.ltm.server.session.SessionStateProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthMessageHandlerTest {
    @Test
    void duplicateRequestIdReturnsErrorWithoutExecutingBusinessActionTwice() throws Exception {
        AuthService authService = mock(AuthService.class);
        SessionManager sessions = mock(SessionManager.class);
        HeartbeatManager heartbeat = mock(HeartbeatManager.class);
        ClientConnection connection = mock(ClientConnection.class);
        when(connection.id()).thenReturn("connection-1");
        when(sessions.findByConnectionId("connection-1")).thenReturn(Optional.empty());
        when(authService.register(any(RegisterRequest.class))).thenReturn(new RegisterResult(
                new PlayerProfileDto(
                        "1",
                        "alice",
                        "Alice",
                        BigDecimal.ZERO,
                        0,
                        0,
                        0,
                        0
                )
        ));
        AuthMessageHandler handler = new AuthMessageHandler(
                authService,
                sessions,
                SessionStateProvider.basic(),
                heartbeat
        );
        MessageEnvelope request = new MessageFactory(new JsonMessageCodec().objectMapper()).request(
                MessageType.REGISTER,
                "same-request-id",
                null,
                new RegisterRequest("alice", "secret", "Alice")
        );

        handler.handle(connection, request);
        handler.handle(connection, request);

        verify(authService, times(1)).register(any(RegisterRequest.class));
        ArgumentCaptor<MessageEnvelope> responses = ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(connection, times(2)).send(responses.capture());
        List<MessageEnvelope> values = responses.getAllValues();
        assertEquals(MessageType.REGISTER, values.getFirst().type());
        assertEquals(MessageType.ERROR, values.getLast().type());
        assertEquals(ErrorCode.INVALID_REQUEST, values.getLast().error().code());
        assertEquals("same-request-id", values.getLast().requestId());
    }
}
