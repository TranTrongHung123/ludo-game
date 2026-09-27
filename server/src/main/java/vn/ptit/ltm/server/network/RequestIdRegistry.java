package vn.ptit.ltm.server.network;

import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

// Nhớ requestId theo từng kết nối trong thời hạn giới hạn để chặn yêu cầu gửi lặp mà không tăng bộ nhớ vô hạn.
final class RequestIdRegistry {
    static final int DEFAULT_MAX_ENTRIES = 16_384;
    static final Duration DEFAULT_RETENTION = Duration.ofMinutes(2);

    private final int maxEntries;
    private final long retentionMillis;
    private final Clock clock;
    private final LinkedHashMap<RequestKey, Long> expiresAtByKey = new LinkedHashMap<>();

    RequestIdRegistry() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_RETENTION, Clock.systemUTC());
    }

    RequestIdRegistry(int maxEntries, Duration retention, Clock clock) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        Objects.requireNonNull(retention, "retention");
        if (retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("retention must be positive");
        }
        this.maxEntries = maxEntries;
        this.retentionMillis = retention.toMillis();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // Từ chối requestId đã gặp trên cùng connection trong khoảng lưu giữ.
    synchronized boolean register(String connectionId, String requestId) {
        RequestKey key = new RequestKey(
                Objects.requireNonNull(connectionId, "connectionId"),
                Objects.requireNonNull(requestId, "requestId")
        );
        long now = clock.millis();
        removeExpired(now);
        Long existingExpiry = expiresAtByKey.get(key);
        if (existingExpiry != null && existingExpiry > now) {
            return false;
        }
        expiresAtByKey.put(key, Math.addExact(now, retentionMillis));
        trimToBound();
        return true;
    }

    // Loại ID hết hạn trước khi kiểm tra yêu cầu mới.
    private void removeExpired(long now) {
        Iterator<Map.Entry<RequestKey, Long>> iterator = expiresAtByKey.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= now) {
                iterator.remove();
            }
        }
    }

    // Bỏ ID cũ nhất khi đạt giới hạn để bộ nhớ chống gửi lặp không tăng vô hạn.
    private void trimToBound() {
        Iterator<RequestKey> iterator = expiresAtByKey.keySet().iterator();
        while (expiresAtByKey.size() > maxEntries && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private record RequestKey(String connectionId, String requestId) {
    }
}
