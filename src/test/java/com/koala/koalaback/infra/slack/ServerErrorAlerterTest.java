package com.koala.koalaback.infra.slack;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("500 알림 억제")
class ServerErrorAlerterTest {
    private static final long COOLDOWN_MS = 600_000L;
    private static final int MAX_PER_HOUR = 20;

    private AdminAlertNotifier notifier;
    private AtomicLong now;
    private ServerErrorAlerter alerter;

    @BeforeEach
    void setUp() {
        notifier = mock(AdminAlertNotifier.class);
        now = new AtomicLong(1_000_000L);
        alerter = new ServerErrorAlerter(notifier, COOLDOWN_MS, MAX_PER_HOUR, now::get);
    }

    @Test
    @DisplayName("첫 오류는 그대로 나간다")
    void firstErrorIsSent() {
        alerter.report(new IllegalStateException("boom"), "GET", "/api/v1/skus");

        verify(notifier).notifyServerError(
                eq("IllegalStateException"), eq("boom"), eq("GET"), eq("/api/v1/skus"), eq(0));
    }

    @Test
    @DisplayName("같은 오류가 쏟아져도 쿨다운 동안 한 번만 나간다")
    void sameErrorIsThrottledWithinCooldown() {
        for (int i = 0; i < 100; i++) {
            alerter.report(new IllegalStateException("boom"), "GET", "/api/v1/skus");
        }

        verify(notifier, times(1))
                .notifyServerError(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("쿨다운이 지나면 다시 나가고, 그동안 묻힌 건수를 함께 알린다")
    void reportsSuppressedCountAfterCooldown() {
        for (int i = 0; i < 50; i++) {
            alerter.report(new IllegalStateException("boom"), "GET", "/api/v1/skus");
        }
        now.addAndGet(COOLDOWN_MS + 1);
        alerter.report(new IllegalStateException("boom"), "GET", "/api/v1/skus");

        verify(notifier).notifyServerError(anyString(), anyString(), anyString(), anyString(), eq(49));
    }

    @Test
    @DisplayName("묻힌 건수는 한 번 보고되면 초기화된다 — 다음 알림에 다시 세지 않는다")
    void suppressedCountResetsAfterReporting() {
        for (int i = 0; i < 5; i++) {
            alerter.report(new IllegalStateException("boom"), "GET", "/a");
        }
        now.addAndGet(COOLDOWN_MS + 1);
        alerter.report(new IllegalStateException("boom"), "GET", "/a");

        now.addAndGet(COOLDOWN_MS + 1);
        alerter.report(new IllegalStateException("boom"), "GET", "/a");

        InOrder inOrder = inOrder(notifier);
        inOrder.verify(notifier).notifyServerError(anyString(), anyString(), anyString(), anyString(), eq(0));
        inOrder.verify(notifier).notifyServerError(anyString(), anyString(), anyString(), anyString(), eq(4));
        inOrder.verify(notifier).notifyServerError(anyString(), anyString(), anyString(), anyString(), eq(0));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("다른 경로에서 난 같은 예외는 별개로 취급한다")
    void differentPathIsSeparateKey() {
        alerter.report(new IllegalStateException("boom"), "GET", "/a");
        alerter.report(new IllegalStateException("boom"), "GET", "/b");

        verify(notifier, times(2))
                .notifyServerError(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("서로 다른 오류가 쏟아져도 시간당 상한을 넘지 않는다")
    void globalHourlyCapHolds() {
        for (int i = 0; i < 100; i++) {
            alerter.report(new IllegalStateException("boom"), "GET", "/api/" + i);
        }

        verify(notifier, times(MAX_PER_HOUR))
                .notifyServerError(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("한 시간이 지나면 상한이 다시 열린다")
    void hourlyCapResets() {
        for (int i = 0; i < 100; i++) {
            alerter.report(new IllegalStateException("boom"), "GET", "/api/" + i);
        }
        now.addAndGet(3_600_001L);
        alerter.report(new IllegalStateException("boom"), "GET", "/api/new");

        verify(notifier, times(MAX_PER_HOUR + 1))
                .notifyServerError(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("알림이 터져도 예외가 새어 나가지 않는다 — 호출부가 예외 처리기다")
    void neverThrowsToCaller() {
        willThrow(new RuntimeException("slack down")).given(notifier)
                .notifyServerError(anyString(), any(), anyString(), anyString(), anyInt());

        alerter.report(new IllegalStateException("boom"), "GET", "/a");
    }

    @Test
    @DisplayName("메시지가 null 인 예외도 처리한다")
    void handlesNullMessage() {
        alerter.report(new NullPointerException(), "POST", "/api/v1/orders");

        verify(notifier).notifyServerError(
                eq("NullPointerException"), eq(null), eq("POST"), eq("/api/v1/orders"), eq(0));
    }

    @Test
    @DisplayName("추적 대상이 상한을 넘으면 비우고 계속 동작한다 — 메모리가 무한히 늘지 않는다")
    void evictsWhenTooManyDistinctKeys() {
        for (int i = 0; i < 600; i++) {
            now.addAndGet(3_600_001L);
            alerter.report(new IllegalStateException("boom"), "GET", "/api/" + i);
        }

        verify(notifier, never()).notifyServerError(
                anyString(), anyString(), anyString(), anyString(), eq(-1));
    }
}
