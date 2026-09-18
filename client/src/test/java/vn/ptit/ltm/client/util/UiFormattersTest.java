package vn.ptit.ltm.client.util;

import org.junit.jupiter.api.Test;
import vn.ptit.ltm.common.enums.MatchParticipantStatus;
import vn.ptit.ltm.common.enums.PieceColor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UiFormattersTest {
    @Test
    void formatsAuthoritativeScoresColorsAndStatusesForVietnameseScreens() {
        assertEquals("+3", UiFormatters.score(new BigDecimal("3.0")));
        assertEquals("+1.5", UiFormatters.score(new BigDecimal("1.5")));
        assertEquals("0", UiFormatters.score(BigDecimal.ZERO));
        assertEquals("Xanh dương", UiFormatters.color(PieceColor.BLUE));
        assertEquals("Bỏ cuộc", UiFormatters.participantStatus(MatchParticipantStatus.FORFEITED));
    }

    @Test
    void formatsServerTimestampWithoutExposingRawEpochValue() {
        String formatted = UiFormatters.dateTime(1_767_225_600_000L);
        assertFalse(formatted.isBlank());
        assertFalse(formatted.contains("1767225600000"));
    }
}
