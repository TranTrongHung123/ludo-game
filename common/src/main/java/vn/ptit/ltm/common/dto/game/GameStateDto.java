package vn.ptit.ltm.common.dto.game;

import vn.ptit.ltm.common.enums.RoomState;
import vn.ptit.ltm.common.enums.TurnState;
import vn.ptit.ltm.common.enums.MatchParticipantStatus;
import vn.ptit.ltm.common.model.BoardConstants;
import vn.ptit.ltm.common.model.GameConstants;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record GameStateDto(
        String roomId,
        String matchId,
        RoomState roomState,
        String currentPlayerId,
        Integer currentSlot,
        TurnState turnState,
        Integer diceValue,
        List<String> validPieceIds,
        long phaseDurationMillis,
        Long serverDeadlineEpochMillis,
        List<MatchParticipantDto> participants,
        List<SpecialCellDto> specialCells,
        long stateVersion,
        MovePresentationDto lastMove
) {
    public GameStateDto(String roomId, String matchId, RoomState roomState, String currentPlayerId,
                        Integer currentSlot, TurnState turnState, Integer diceValue, List<String> validPieceIds,
                        long phaseDurationMillis, Long serverDeadlineEpochMillis, List<MatchParticipantDto> participants,
                        List<SpecialCellDto> specialCells, long stateVersion) {
        this(roomId, matchId, roomState, currentPlayerId, currentSlot, turnState, diceValue, validPieceIds,
                phaseDurationMillis, serverDeadlineEpochMillis, participants, specialCells, stateVersion, null);
    }

    public GameStateDto withLastMove(MovePresentationDto move) {
        return new GameStateDto(roomId, matchId, roomState, currentPlayerId, currentSlot, turnState, diceValue,
                validPieceIds, phaseDurationMillis, serverDeadlineEpochMillis, participants, specialCells, stateVersion, move);
    }

    public GameStateDto {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(roomState, "roomState");
        Objects.requireNonNull(turnState, "turnState");
        validPieceIds = validPieceIds == null ? List.of() : List.copyOf(validPieceIds);
        participants = participants == null ? List.of() : List.copyOf(participants);
        specialCells = specialCells == null ? List.of() : List.copyOf(specialCells);
        if ((currentPlayerId == null) != (currentSlot == null)) {
            throw new IllegalArgumentException("currentPlayerId and currentSlot must both be set or both be null");
        }
        if (currentSlot != null && (currentSlot < 0 || currentSlot >= BoardConstants.MAX_PLAYERS)) {
            throw new IllegalArgumentException("currentSlot must be between 0 and 3");
        }
        if (diceValue != null && (diceValue < GameConstants.DICE_MIN || diceValue > GameConstants.DICE_MAX)) {
            throw new IllegalArgumentException("diceValue must be between 1 and 6");
        }
        if (phaseDurationMillis < 0 || stateVersion < 0) {
            throw new IllegalArgumentException("Durations and stateVersion must not be negative");
        }
        if (new HashSet<>(participants.stream().map(MatchParticipantDto::slotIndex).toList()).size()
                != participants.size()) {
            throw new IllegalArgumentException("Participant slots must be unique");
        }
        if (participants.size() < BoardConstants.MIN_PLAYERS || participants.size() > BoardConstants.MAX_PLAYERS) {
            throw new IllegalArgumentException("A game must contain between 2 and 4 participants");
        }
        if (new HashSet<>(participants.stream().map(MatchParticipantDto::playerId).toList()).size()
                != participants.size()) {
            throw new IllegalArgumentException("Participant identifiers must be unique");
        }
        if (new HashSet<>(specialCells.stream().map(SpecialCellDto::globalIndex).toList()).size()
                != specialCells.size()) {
            throw new IllegalArgumentException("Special cell positions must be unique");
        }
        if (currentPlayerId != null && participants.stream().noneMatch(participant ->
                participant.playerId().equals(currentPlayerId)
                        && participant.slotIndex() == currentSlot
                        && participant.matchStatus() == MatchParticipantStatus.ACTIVE)) {
            throw new IllegalArgumentException("Current player must identify an ACTIVE participant at currentSlot");
        }
    }
}
