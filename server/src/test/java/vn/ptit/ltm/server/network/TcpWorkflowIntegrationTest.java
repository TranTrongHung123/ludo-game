package vn.ptit.ltm.server.network;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.protocol.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the public wire contract without any desktop client implementation. */
class TcpWorkflowIntegrationTest {
    @Test
    void registerLoginLogout() throws Exception {
        try (var server = new WorkflowServer(new WorkflowServer.InMemoryUserRepository()); var client = new Peer(server.port())) {
            var registered = client.ok("REGISTER", Map.of("username","alice","password","secret","displayName","Alice"));
            assertEquals("alice", registered.path("profile").path("username").asText());
            client.login("alice");
            assertEquals(1, server.sessions().activeSessionCount());
            client.ok("LOGOUT", Map.of());
            assertEquals(0, server.sessions().activeSessionCount());
        }
    }

    @Test
    void authenticationErrorsAreReturnedOverTcp() throws Exception {
        try (var server = new WorkflowServer(new WorkflowServer.InMemoryUserRepository()); var client = new Peer(server.port())) {
            client.register("alice");
            var response=client.request("LOGIN", Map.of("username","alice","password","wrong"));
            assertEquals(MessageType.ERROR,response.type());
            assertEquals("INVALID_CREDENTIALS",response.error().code().name());
            assertEquals(0,server.sessions().activeSessionCount());
        }
    }

    @Test
    void roomLifecycleBroadcastsToOtherPlayers() throws Exception {
        try(var server=new WorkflowServer(new WorkflowServer.InMemoryUserRepository());var a=new Peer(server.port());var b=new Peer(server.port())) {
            a.registerLogin("alice");b.registerLogin("bob");
            assertEquals(2,a.ok("GET_ONLINE_PLAYERS",Map.of()).path("players").size());
            String room=a.createRoom();b.ok("JOIN_ROOM",Map.of("roomId",room));
            assertEquals(2,a.event("ROOM_UPDATED",d->d.path("room").path("players").size()==2).path("room").path("players").size());
            b.ok("LEAVE_ROOM",Map.of("roomId",room));
            a.event("ROOM_UPDATED",d->d.path("room").path("players").size()==1);
        }
    }

    @Test
    void invitationsReadyStartAndMovesUseAuthoritativeSnapshots() throws Exception {
        try(var server=new WorkflowServer(new WorkflowServer.InMemoryUserRepository());var a=new Peer(server.port());var b=new Peer(server.port())) {
            a.registerLogin("alice");b.registerLogin("bob");String room=a.createRoom();
            a.ok("INVITE_PLAYER",Map.of("roomId",room,"playerId","2"));
            String rejected=b.event("INVITE_PLAYER",d->true).path("invitationId").asText();
            b.ok("REJECT_INVITE",Map.of("invitationId",rejected));
            a.ok("INVITE_PLAYER",Map.of("roomId",room,"playerId","2"));
            b.ok("ACCEPT_INVITE",Map.of("invitationId",b.event("INVITE_PLAYER",d->!d.path("invitationId").asText().equals(rejected)).path("invitationId").asText()));
            assertEquals("NOT_ROOM_HOST",b.request("START_GAME",Map.of("roomId",room)).error().code().name());
            a.ok("READY",Map.of("roomId",room,"ready",true));b.ok("READY",Map.of("roomId",room,"ready",true));
            a.ok("START_GAME",Map.of("roomId",room));
            var initial=b.event("GAME_STATE",d->true);assertEquals("1",initial.path("currentPlayerId").asText());
            assertEquals("NOT_YOUR_TURN",b.request("ROLL_DICE",Map.of("roomId",room)).error().code().name());
            var dice=a.ok("ROLL_DICE",Map.of("roomId",room));assertEquals(6,dice.path("diceValue").asInt());
            String piece=dice.path("validPieceIds").get(0).asText();
            var spawn=a.ok("MOVE_PIECE",Map.of("roomId",room,"pieceId",piece));
            assertEquals(0,spawn.path("piece").path("stepCount").asInt());assertTrue(spawn.path("bonusRoll").asBoolean());
            assertEquals(3,a.ok("ROLL_DICE",Map.of("roomId",room)).path("diceValue").asInt());
            var moved=a.ok("MOVE_PIECE",Map.of("roomId",room,"pieceId",piece));
            assertEquals(3,moved.path("piece").path("stepCount").asInt());assertFalse(moved.path("bonusRoll").asBoolean());
            b.event("GAME_STATE_UPDATED",d->d.path("stateVersion").asLong()==moved.path("gameState").path("stateVersion").asLong());
        }
    }

    @Test
    void rankingAndHistoryRequireAuthentication() throws Exception {
        try(var server=new WorkflowServer(new WorkflowServer.InMemoryUserRepository());var a=new Peer(server.port())) {
            assertEquals(MessageType.ERROR,a.request("GET_RANKING",Map.of()).type());
            a.registerLogin("alice");
            assertEquals("1",a.ok("GET_RANKING",Map.of()).path("entries").get(0).path("playerId").asText());
            assertEquals("stored-match",a.ok("GET_MATCH_HISTORY",Map.of()).path("matches").get(0).path("matchId").asText());
        }
    }

    @Test
    void quitForfeitsAndWinnerCanLeaveAndCreateAnotherRoom() throws Exception {
        try(var server=new WorkflowServer(new WorkflowServer.InMemoryUserRepository());var a=new Peer(server.port());var b=new Peer(server.port())) {
            String room=start(a,b);b.ok("LEAVE_ROOM",Map.of("roomId",room));
            var over=a.event("GAME_OVER",d->true);
            assertTrue(over.path("standings").toString().contains("FORFEITED"));
            var finished=a.event("GAME_STATE_UPDATED",d->d.path("roomState").asText().equals("FINISHED"));
            assertEquals("COMPLETED",finished.path("participants").get(0).path("matchStatus").asText());
            assertEquals("FORFEITED",finished.path("participants").get(1).path("matchStatus").asText());
            a.ok("LEAVE_ROOM",Map.of("roomId",room));assertFalse(a.createRoom().isBlank());
        }
    }

    @Test
    void reconnectRestoresMovePhaseAndDeadline() throws Exception {
        try(var server=new WorkflowServer(new WorkflowServer.InMemoryUserRepository());var a=new Peer(server.port());var b=new Peer(server.port())) {
            String room=start(a,b);var dice=a.ok("ROLL_DICE",Map.of("roomId",room));
            var before=a.event("GAME_STATE_UPDATED",d->d.path("turnState").asText().equals("WAITING_FOR_MOVE"));
            String token=a.session;a.close();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(server.sessions().findByUserId(1L).orElseThrow().presenceState()!=vn.ptit.ltm.common.enums.PlayerPresenceState.DISCONNECTED&&System.nanoTime()<deadline)Thread.sleep(10);
            try(var restored=new Peer(server.port())) {
                var result=restored.ok("RECONNECT",Map.of("sessionId",token));restored.session=token;
                assertTrue(result.path("restored").asBoolean());
                assertEquals(before.path("serverDeadlineEpochMillis"),result.path("gameState").path("serverDeadlineEpochMillis"));
                assertEquals(before.path("validPieceIds"),result.path("gameState").path("validPieceIds"));
                assertEquals(0,restored.ok("MOVE_PIECE",Map.of("roomId",room,"pieceId",dice.path("validPieceIds").get(0).asText())).path("piece").path("stepCount").asInt());
            }
        }
    }

    private static String start(Peer a,Peer b) throws Exception {
        a.registerLogin("alice");b.registerLogin("bob");String room=a.createRoom();
        b.ok("JOIN_ROOM",Map.of("roomId",room));a.ok("READY",Map.of("roomId",room,"ready",true));b.ok("READY",Map.of("roomId",room,"ready",true));a.ok("START_GAME",Map.of("roomId",room));return room;
    }

    private static final class Peer implements AutoCloseable {
        final Socket socket;final MessageIO io=new MessageIO();final MessageFactory factory=new MessageFactory(new JsonMessageCodec().objectMapper());
        final List<MessageEnvelope> events=new ArrayList<>();String session;
        Peer(int port)throws Exception {socket=new Socket("127.0.0.1",port);socket.setSoTimeout(3000);}
        MessageEnvelope request(String type,Object data)throws Exception {
            String id=UUID.randomUUID().toString();io.write(socket.getOutputStream(),factory.request(MessageType.valueOf(type),id,session,data));
            while(true){var message=io.read(socket.getInputStream());if(id.equals(message.requestId()))return message;events.add(message);}
        }
        JsonNode ok(String type,Object data)throws Exception {var response=request(type,data);assertEquals(Boolean.TRUE,response.success(),()->type+": "+response.error());return response.data();}
        JsonNode event(String type,Predicate<JsonNode> predicate)throws Exception {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(System.nanoTime()<deadline){for(var it=events.iterator();it.hasNext();){var e=it.next();if(e.type().name().equals(type)&&predicate.test(e.data())){it.remove();return e.data();}}events.add(io.read(socket.getInputStream()));}
            throw new AssertionError("Missing event "+type);
        }
        void register(String name)throws Exception {ok("REGISTER",Map.of("username",name,"password","secret","displayName",name));}
        void login(String name)throws Exception {session=ok("LOGIN",Map.of("username",name,"password","secret")).path("sessionId").asText();}
        void registerLogin(String name)throws Exception{register(name);login(name);}
        String createRoom()throws Exception{return ok("CREATE_ROOM",Map.of()).path("room").path("roomId").asText();}
        public void close()throws Exception{socket.close();}
    }
}
