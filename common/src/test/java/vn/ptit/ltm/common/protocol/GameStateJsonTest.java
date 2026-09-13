package vn.ptit.ltm.common.protocol;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.dto.game.GameStateDto;
import vn.ptit.ltm.common.dto.game.MatchParticipantDto;
import vn.ptit.ltm.common.dto.game.PieceDto;
import vn.ptit.ltm.common.dto.game.SpecialCellDto;
import vn.ptit.ltm.common.enums.MatchParticipantStatus;
import vn.ptit.ltm.common.enums.MessageType;
import vn.ptit.ltm.common.enums.PieceColor;
import vn.ptit.ltm.common.enums.PieceState;
import vn.ptit.ltm.common.enums.PlayerPresenceState;
import vn.ptit.ltm.common.enums.RoomState;
import vn.ptit.ltm.common.enums.SpecialCellType;
import vn.ptit.ltm.common.enums.TurnState;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameStateJsonTest {
    @Test
    void roundTripsCompleteGameStateAndKeepsCollectionsImmutable() throws Exception {
        JsonMessageCodec codec = new JsonMessageCodec();
        MessageFactory messages = new MessageFactory(codec.objectMapper());
        PayloadMapper payloads = new PayloadMapper(codec.objectMapper());
        List<String> validPieces = new ArrayList<>(List.of("red-1"));
        GameStateDto state = new GameStateDto(
                "room-1",
                "match-1",
                RoomState.PLAYING,
                "player-red",
                0,
                TurnState.WAITING_FOR_MOVE,
                6,
                validPieces,
                12_000,
                1_800_000_000_000L,
                List.of(participant("player-red", PieceColor.RED), participant("player-blue", PieceColor.BLUE)),
                List.of(new SpecialCellDto(1, SpecialCellType.SPEED)),
                7
        );
        validPieces.add("red-2");

        MessageEnvelope envelope = messages.event(MessageType.GAME_STATE, state);
        GameStateDto decoded = payloads.fromTree(codec.decode(codec.encode(envelope)).data(), GameStateDto.class);

        assertEquals(state, decoded);
        assertEquals(List.of("red-1"), decoded.validPieceIds());
        assertThrows(UnsupportedOperationException.class, () -> decoded.validPieceIds().add("red-2"));
        assertThrows(UnsupportedOperationException.class, () -> decoded.participants().clear());
    }

    private static MatchParticipantDto participant(String playerId, PieceColor color) {
        List<PieceDto> pieces = new ArrayList<>();
        for (int index = 1; index <= 4; index++) {
            pieces.add(new PieceDto(
                    color.name().toLowerCase() + "-" + index,
                    playerId,
                    color,
                    PieceState.IN_YARD,
                    -1,
                    false,
                    false
            ));
        }
        return new MatchParticipantDto(
                playerId,
                playerId,
                color.slotIndex(),
                color,
                PlayerPresenceState.PLAYING,
                MatchParticipantStatus.ACTIVE,
                null,
                null,
                pieces
        );
    }
}
