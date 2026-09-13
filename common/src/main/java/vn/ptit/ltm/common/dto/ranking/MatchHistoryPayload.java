package vn.ptit.ltm.common.dto.ranking;

import java.util.List;

public record MatchHistoryPayload(List<MatchHistoryEntryDto> matches) {
    public MatchHistoryPayload {
        matches = matches == null ? List.of() : List.copyOf(matches);
    }
}
