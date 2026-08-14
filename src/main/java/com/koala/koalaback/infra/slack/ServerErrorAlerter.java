package com.koala.koalaback.infra.slack;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

@Slf4j
@Component
public class ServerErrorAlerter {
    private final AdminAlertNotifier adminAlertNotifier;
    private final long cooldownMs;
    private final int maxPerHour;
    private final LongSupplier clock;

    private static final int MAX_TRACKED_KEYS = 500;

    private final Map<String, Long> lastSentAt = new ConcurrentHashMap<>();
    private final Map<String, Integer> suppressedCount = new ConcurrentHashMap<>();

    private volatile long windowStartedAt;
    private volatile int sentInWindow;

    @Autowired
    public ServerErrorAlerter(AdminAlertNotifier adminAlertNotifier,
                              @Value("${koala.alert.error-cooldown-ms:600000}") long cooldownMs,
                              @Value("${koala.alert.error-max-per-hour:20}") int maxPerHour) {
        this(adminAlertNotifier, cooldownMs, maxPerHour, System::currentTimeMillis);
    }

    ServerErrorAlerter(AdminAlertNotifier adminAlertNotifier,
                       long cooldownMs, int maxPerHour, LongSupplier clock) {
        this.adminAlertNotifier = adminAlertNotifier;
        this.cooldownMs = cooldownMs;
        this.maxPerHour = maxPerHour;
        this.clock = clock;
        this.windowStartedAt = clock.getAsLong();
    }

    public void report(Throwable error, String method, String uri) {
        try {
            String key = error.getClass().getSimpleName() + " " + method + " " + uri;

            int suppressed = claimSendSlot(key);
            if (suppressed < 0) return;

            adminAlertNotifier.notifyServerError(
                    error.getClass().getSimpleName(), error.getMessage(), method, uri, suppressed);
        } catch (Exception e) {
            log.warn("500 알림 실패: {}", e.getMessage());
        }
    }

    private synchronized int claimSendSlot(String key) {
        long now = clock.getAsLong();

        if (now - windowStartedAt >= 3_600_000L) {
            windowStartedAt = now;
            sentInWindow = 0;
        }

        if (lastSentAt.size() > MAX_TRACKED_KEYS) {
            lastSentAt.clear();
            suppressedCount.clear();
        }

        Long last = lastSentAt.get(key);
        boolean withinCooldown = last != null && now - last < cooldownMs;

        if (withinCooldown || sentInWindow >= maxPerHour) {
            suppressedCount.merge(key, 1, Integer::sum);
            return -1;
        }

        lastSentAt.put(key, now);
        sentInWindow++;

        Integer suppressed = suppressedCount.remove(key);
        return suppressed != null ? suppressed : 0;
    }
}
