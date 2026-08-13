package com.koala.koalaback.domain.settlement.entity;

import com.koala.koalaback.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 월별 작가 정산 — <b>확정된 것만</b> 행으로 남는다.
 *
 * <p>확정 전에는 행이 없다. 조회할 때마다 주문에서 그때그때 계산해 보여준다.
 * 확정하는 순간의 값을 여기에 굳히는 이유는, 다시 계산하면 <b>이미 지급한 달의 숫자가
 * 나중에 달라지기</b> 때문이다 — 반품이 뒤늦게 승인되거나 수수료율을 조정하면 그렇게 된다.
 *
 * <p>그래서 {@code commissionRate} 도 확정 시점의 값을 복사해 둔다. 작가 테이블을
 * 참조하면 요율을 바꾼 순간 과거 정산액이 전부 바뀐다.
 */
@Entity
@Table(name = "artist_settlements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtistSettlement extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long artistId;

    /** 정산 월 — "2026-08" */
    @Column(nullable = false, length = 7, updatable = false)
    private String periodYm;

    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal grossAmount;

    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal refundAmount;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate;

    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal commissionAmount;

    /** 실제로 작가에게 보낼 금액 — 계산식이 바뀌어도 이 값은 바뀌지 않는다 */
    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal payoutAmount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private LocalDateTime confirmedAt;

    private LocalDateTime paidAt;

    @Column(length = 500)
    private String memo;

    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_PAID      = "PAID";

    @Builder
    public ArtistSettlement(Long artistId, String periodYm,
                            BigDecimal grossAmount, BigDecimal refundAmount,
                            BigDecimal commissionRate, BigDecimal commissionAmount,
                            BigDecimal payoutAmount) {
        this.artistId         = artistId;
        this.periodYm         = periodYm;
        this.grossAmount      = grossAmount;
        this.refundAmount     = refundAmount;
        this.commissionRate   = commissionRate;
        this.commissionAmount = commissionAmount;
        this.payoutAmount     = payoutAmount;
        this.status           = STATUS_CONFIRMED;
        this.confirmedAt      = LocalDateTime.now();
    }

    /**
     * 지급 완료 표시.
     *
     * <p>이미 지급한 건을 다시 지급 처리하지 못하게 막는다. 돈이 두 번 나가는 실수는
     * 되돌리기 어렵고, 어드민에서 버튼을 두 번 누르는 일은 실제로 일어난다.
     */
    public void markPaid(String memo) {
        if (STATUS_PAID.equals(this.status)) {
            throw new IllegalStateException("이미 지급 완료된 정산입니다.");
        }
        this.status = STATUS_PAID;
        this.paidAt = LocalDateTime.now();
        this.memo   = memo;
    }

    public boolean isPaid() { return STATUS_PAID.equals(this.status); }
}
