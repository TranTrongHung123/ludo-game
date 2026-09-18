package vn.ptit.ltm.client.util;

import vn.ptit.ltm.common.enums.MatchParticipantStatus;
import vn.ptit.ltm.common.enums.PieceColor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class UiFormatters {
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private UiFormatters() {
    }

    public static String score(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        String plain = value.stripTrailingZeros().toPlainString();
        return value.signum() > 0 ? "+" + plain : plain;
    }

    public static String dateTime(long epochMillis) {
        if (epochMillis <= 0) {
            throw new IllegalArgumentException("epochMillis must be positive");
        }
        return DATE_TIME.format(Instant.ofEpochMilli(epochMillis));
    }

    public static String color(PieceColor color) {
        return switch (Objects.requireNonNull(color, "color")) {
            case RED -> "Đỏ";
            case BLUE -> "Xanh dương";
            case YELLOW -> "Vàng";
            case GREEN -> "Xanh lá";
        };
    }

    public static String participantStatus(MatchParticipantStatus status) {
        return switch (Objects.requireNonNull(status, "status")) {
            case COMPLETED -> "Hoàn thành";
            case FORFEITED -> "Bỏ cuộc";
            case ACTIVE -> "Đang thi đấu";
        };
    }
}
