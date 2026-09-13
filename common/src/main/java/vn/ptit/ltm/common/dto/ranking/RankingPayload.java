package vn.ptit.ltm.common.dto.ranking;

import java.util.List;

public record RankingPayload(List<RankingEntryDto> entries) {
    public RankingPayload {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
