package com.koala.koalaback.domain.payment.entity;

import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, unique = true, length = 40)
    private String paymentNo;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(nullable = false, length = 30)
    private String method;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal requestedAmount;

    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal approvedAmount;

    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal cancelledAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 100)
    private String pgTransactionId;

    @Column(length = 100)
    private String approvalNo;

    @Column(length = 100)
    private String failureCode;

    @Column(length = 255)
    private String failureMessage;

    private LocalDateTime approvedAt;
    private LocalDateTime failedAt;
    private LocalDateTime cancelledAt;

    @Column(columnDefinition = "JSON")
    private String rawResponseJson;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentEvent> events = new ArrayList<>();

    @Builder
    public Payment(Order order, String paymentNo, String provider,
                   String method, BigDecimal requestedAmount, String currency) {
        this.order = order;
        this.paymentNo = paymentNo;
        this.provider = provider;
        this.method = method;
        this.status = "READY";
        this.requestedAmount = requestedAmount;
        this.approvedAmount = BigDecimal.ZERO;
        this.cancelledAmount = BigDecimal.ZERO;
        this.currency = currency != null ? currency : "KRW";
    }

    /**
     * PG 승인 요청 직전 선점 — 같은 결제가 두 번 승인 요청되는 것을 막는다.
     * 이 상태로 커밋한 뒤에야 트랜잭션 밖에서 PG 를 호출한다.
     */
    public void markInProgress() {
        this.status = "IN_PROGRESS";
    }

    /**
     * 승인 여부 미확정 — 타임아웃/5xx 로 PG 응답을 못 받았고 재조회로도 확정하지 못한 상태.
     *
     * <p>절대 FAILED 로 두지 않는다. 실제로는 승인되어 돈이 빠져나갔을 수 있으므로,
     * 실패로 단정하면 주문이 만료 취소되어 "결제됐는데 주문 없음"이 된다.
     * 웹훅 또는 재조회로 확정될 때까지 이 상태로 남긴다.
     */
    public void markInDoubt(String failureCode, String failureMessage) {
        this.status = "IN_DOUBT";
        this.failureCode = failureCode;
        this.failureMessage = truncate(failureMessage);
    }

    /** 환불 요청 직전 선점 — 동시 이중 환불 방지 */
    public void markCancelInProgress() {
        this.status = "CANCEL_IN_PROGRESS";
    }

    /** 환불 실패로 원래 상태(CAPTURED)로 되돌린다 */
    public void revertToCaptured() {
        this.status = "CAPTURED";
    }

    public void markCaptured(String pgTransactionId, String approvalNo,
                             BigDecimal approvedAmount, String rawResponseJson) {
        this.status = "CAPTURED";
        this.pgTransactionId = pgTransactionId;
        this.approvalNo = approvalNo;
        this.approvedAmount = approvedAmount;
        this.rawResponseJson = rawResponseJson;
        this.approvedAt = LocalDateTime.now();
    }

    public void markFailed(String failureCode, String failureMessage) {
        this.status = "FAILED";
        this.failureCode = failureCode;
        this.failureMessage = truncate(failureMessage);
        this.failedAt = LocalDateTime.now();
    }

    public void markCancelled(BigDecimal cancelAmount) {
        this.cancelledAmount = this.cancelledAmount.add(cancelAmount);
        this.status = this.cancelledAmount.compareTo(this.approvedAmount) >= 0
                ? "CANCELLED" : "PARTIAL_REFUNDED";
        this.cancelledAt = LocalDateTime.now();
    }

    public boolean isCaptured()   { return "CAPTURED".equals(this.status); }
    public boolean isReady()      { return "READY".equals(this.status); }
    public boolean isInProgress() { return "IN_PROGRESS".equals(this.status); }
    public boolean isInDoubt()    { return "IN_DOUBT".equals(this.status); }

    /**
     * 결과가 아직 정해지지 않아 주문을 만료 취소하면 안 되는 상태인지.
     * 만료 스케줄러가 이 결제를 가진 주문을 건너뛰는 기준.
     */
    public boolean isSettlementPending() {
        return isInProgress() || isInDoubt();
    }

    /** failure_message 컬럼이 varchar(255) — PG 원문이 길면 저장 시 터진다 */
    private String truncate(String message) {
        if (message == null) return null;
        return message.length() <= 255 ? message : message.substring(0, 255);
    }
}