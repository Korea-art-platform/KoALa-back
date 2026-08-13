package com.koala.koalaback.infra.slack;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 처리되지 않은 500 오류를 슬랙으로 올린다.
 *
 * <h3>왜 로그로 충분하지 않은가</h3>
 * <p>로그는 <b>이미 문제가 있다는 걸 아는 사람만</b> 본다. 고객이 문의를 넣기 전까지
 * 서버가 500을 뿌리고 있어도 아무도 모른다.
 *
 * <h3>왜 폭주를 막아야 하는가</h3>
 * <p>500 은 하나씩 오지 않는다. DB 커넥션이 마르면 초당 수십 건이 같은 예외로 터진다.
 * 그대로 흘려보내면 채널이 잠겨 <b>정작 중요한 주문·환불 알림이 묻힌다.</b>
 * 그래서 두 겹으로 막는다.
 *
 * <ul>
 *   <li><b>같은 오류</b>(예외 종류 + 경로)는 쿨다운 동안 한 번만 — 같은 사실을 반복해도 정보가 늘지 않는다</li>
 *   <li><b>전체 건수</b>도 시간당 상한 — 서로 다른 오류가 동시에 쏟아지는 경우까지 막는다</li>
 * </ul>
 *
 * <p>억제된 건수는 다음 알림에 함께 실어 보낸다. 조용해진 것과 억눌린 것은 다르고,
 * 그 차이가 장애 규모를 말해 준다.
 */
@Slf4j
@Component
public class ServerErrorAlerter {

    private final AdminAlertNotifier adminAlertNotifier;
    private final long cooldownMs;
    private final int maxPerHour;
    private final LongSupplier clock;

    /** 서로 다른 오류 종류를 추적하는 상한 — 넘으면 통째로 비운다(메모리 누수 방지) */
    private static final int MAX_TRACKED_KEYS = 500;

    private final Map<String, Long> lastSentAt = new ConcurrentHashMap<>();
    private final Map<String, Integer> suppressedCount = new ConcurrentHashMap<>();

    private volatile long windowStartedAt;
    private volatile int sentInWindow;

    // 생성자가 둘이라 어느 쪽을 쓸지 명시해야 한다.
    // 표시하지 않으면 스프링이 기본 생성자를 찾다가 실패해 컨텍스트 전체가 뜨지 않는다.
    @Autowired
    public ServerErrorAlerter(AdminAlertNotifier adminAlertNotifier,
                              @Value("${koala.alert.error-cooldown-ms:600000}") long cooldownMs,
                              @Value("${koala.alert.error-max-per-hour:20}") int maxPerHour) {
        this(adminAlertNotifier, cooldownMs, maxPerHour, System::currentTimeMillis);
    }

    /** 테스트용 — 시계를 주입해 쿨다운 경과를 기다리지 않고 검증한다 */
    ServerErrorAlerter(AdminAlertNotifier adminAlertNotifier,
                       long cooldownMs, int maxPerHour, LongSupplier clock) {
        this.adminAlertNotifier = adminAlertNotifier;
        this.cooldownMs = cooldownMs;
        this.maxPerHour = maxPerHour;
        this.clock = clock;
        this.windowStartedAt = clock.getAsLong();
    }

    /**
     * 500 오류 한 건을 보고한다.
     *
     * <p>예외를 던지지 않는다 — 호출부가 예외 처리기다. 여기서 또 터지면
     * 고객은 오류 응답조차 받지 못한다.
     */
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

    /**
     * 쿨다운과 시간당 상한을 함께 판정하고, 보낼 수 있으면 그동안 억제된 건수를 돌려준다.
     *
     * <p>판정과 억제 카운트 회수를 한 락 안에서 처리한다. 나눠 놓으면 그 사이에 들어온
     * 억제 건이 사라져 "묻힌 건수"가 실제보다 적게 보고된다.
     *
     * @return 보낼 수 있으면 그동안 억제된 건수(0 이상), 보내지 않을 상황이면 -1
     */
    private synchronized int claimSendSlot(String key) {
        long now = clock.getAsLong();

        if (now - windowStartedAt >= 3_600_000L) {
            windowStartedAt = now;
            sentInWindow = 0;
        }

        // 종류가 계속 늘어나는 상황(경로에 ID 가 박히는 등)에서 맵이 무한히 커지지 않게 한다.
        // 통째로 비우면 다음 한 건은 알림이 나가는데, 그게 조용히 잃는 것보다 낫다.
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
