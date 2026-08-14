package com.koala.koalaback.infra.slack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("운영 알림")
class AdminAlertNotifierTest {
    @SuppressWarnings("unchecked")
    private final ObjectProvider<SlackNotifier> provider = mock(ObjectProvider.class);
    private final AdminAlertNotifier notifier = new AdminAlertNotifier(provider);

    @Nested
    @DisplayName("메시지 내용")
    class MessageContent {
        @Test
        @DisplayName("반품 신청 — 접수번호·주문번호·신청자·사유가 들어간다")
        void returnRequested() {
            String message = notifier.buildReturnRequested(
                    "RET-1", "ORD-20260813-1", "RETURN", "단순 변심", "홍길동");

            assertThat(message)
                    .contains("반품")
                    .contains("RET-1")
                    .contains("ORD-20260813-1")
                    .contains("홍길동")
                    .contains("단순 변심");
        }

        @Test
        @DisplayName("교환 신청은 '교환' 으로 표시된다 — 처리 방법이 다르다")
        void exchangeIsLabelledDifferently() {
            assertThat(notifier.buildReturnRequested("RET-2", "ORD-1", "EXCHANGE", "파손", "김철수"))
                    .contains("교환")
                    .doesNotContain("반품");
        }

        @Test
        @DisplayName("결제 미확정 — 금액과 '확인이 필요하다'는 사실이 드러난다")
        void paymentInDoubt() {
            String message = notifier.buildPaymentInDoubt(
                    "ORD-1", new BigDecimal("150000"), "PG 재조회 실패");

            assertThat(message)
                    .contains("150,000원")
                    .contains("ORD-1")
                    .contains("확인");
        }

        @Test
        @DisplayName("수동 환불 — 거래번호가 들어간다. 이게 없으면 PG 콘솔에서 찾을 수 없다")
        void manualRefundCarriesTransactionId() {
            String message = notifier.buildManualRefundNeeded(
                    "ORD-1", new BigDecimal("99000"), "pk_abc123", "timeout");

            assertThat(message)
                    .contains("99,000원")
                    .contains("pk_abc123")
                    .contains("timeout");
        }

        @Test
        @DisplayName("거래번호가 없는 경우에도 메시지를 만든다")
        void manualRefundWithoutTransactionId() {
            assertThat(notifier.buildManualRefundNeeded("ORD-1", new BigDecimal("1000"), null, "사유"))
                    .contains("ORD-1")
                    .doesNotContain("null");
        }

        @Test
        @DisplayName("환불 미확정 — 결제번호로 식별한다 (이 경로에는 주문번호가 없다)")
        void cancelInDoubtUsesPaymentNo() {
            assertThat(notifier.buildCancelInDoubt("PAY-9", new BigDecimal("20000"), "PROVIDER_EXCEPTION"))
                    .contains("PAY-9")
                    .contains("20,000원");
        }

        @Test
        @DisplayName("재고 소진 — 작가명이 없어도 빈 괄호가 남지 않는다")
        void stockDepletedWithoutArtist() {
            assertThat(notifier.buildStockDepleted("SKU-1", "푸른 곰", null))
                    .contains("푸른 곰")
                    .contains("SKU-1")
                    .doesNotContain("[]");
        }

        @Test
        @DisplayName("기동 알림 — 배포하지 않았는데 올라오면 이상 신호라는 안내가 붙는다")
        void applicationStarted() {
            assertThat(notifier.buildApplicationStarted("prod", "2026-08-13T10:00"))
                    .contains("prod")
                    .contains("롤백");
        }
    }

    @Nested
    @DisplayName("알림 실패가 업무를 막지 않는다")
    class NeverBreaksCallerFlow {
        @Test
        @DisplayName("슬랙이 꺼져 있으면(빈 없음) 조용히 통과한다")
        void slackDisabled() {
            given(provider.getIfAvailable()).willReturn(null);

            assertThatCode(() -> notifier.notifyReturnRequested("R", "O", "RETURN", "사유", "홍길동"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("슬랙 발송이 예외를 던져도 호출자에게 전파되지 않는다")
        void sendThrows() {
            SlackNotifier slack = mock(SlackNotifier.class);
            given(provider.getIfAvailable()).willReturn(slack);
            willThrow(new RuntimeException("boom")).given(slack).send(anyString());

            assertThatCode(() -> notifier.notifyManualRefundNeeded(
                    "ORD-1", BigDecimal.TEN, "pk_1", "사유"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("빈 조회 자체가 터져도 삼킨다")
        void providerThrows() {
            given(provider.getIfAvailable()).willThrow(new IllegalStateException("컨텍스트 종료"));

            assertThatCode(() -> notifier.notifyPaymentInDoubt("ORD-1", BigDecimal.TEN, "사유"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("메시지를 만들다 터져도 삼킨다 — 조립도 방어 범위 안이다")
        void messageBuildingThrows() {
            SlackNotifier slack = mock(SlackNotifier.class);
            given(provider.getIfAvailable()).willReturn(slack);

            assertThatCode(() -> notifier.notifyReturnRequested(null, null, null, null, null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("슬랙이 꺼져 있으면 발송을 시도조차 하지 않는다")
        void doesNotAttemptSendWhenDisabled() {
            SlackNotifier slack = mock(SlackNotifier.class);
            given(provider.getIfAvailable()).willReturn(null);

            notifier.notifyStockDepleted("SKU-1", "푸른 곰", "김작가");

            verify(slack, never()).send(anyString());
        }
    }
}
