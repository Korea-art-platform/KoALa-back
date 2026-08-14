package com.koala.koalaback.domain.settlement.entity;

import com.koala.koalaback.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "artist_settlements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtistSettlement extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long artistId;

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
