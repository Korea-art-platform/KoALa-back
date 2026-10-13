package com.koala.koalaback.domain.payment.repository;

import com.koala.koalaback.domain.payment.dto.DailyRevenueProjection;
import com.koala.koalaback.domain.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPaymentNo(String paymentNo);

    Optional<Payment> findByPgTransactionId(String pgTransactionId);

    Optional<Payment> findTopByOrderIdOrderByCreatedAtDesc(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findTopWithLockByOrderIdOrderByCreatedAtDesc(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findWithLockByPaymentNo(String paymentNo);

    List<Payment> findByStatusInOrderByCreatedAtDesc(List<String> statuses);

    @Query("""
        SELECT COALESCE(SUM(p.approvedAmount - COALESCE(p.cancelledAmount, 0)), 0) FROM Payment p
        WHERE p.approvedAt >= :from
          AND p.status IN ('CAPTURED', 'PARTIAL_REFUNDED')
          AND p.order.orderStatus <> 'CANCELLED'
        """)
    BigDecimal sumApprovedAmountAfter(@Param("from") LocalDateTime from);

    @Query("""
        SELECT COALESCE(SUM(p.approvedAmount - COALESCE(p.cancelledAmount, 0)), 0) FROM Payment p
        WHERE p.status IN ('CAPTURED', 'PARTIAL_REFUNDED')
          AND p.order.orderStatus <> 'CANCELLED'
        """)
    BigDecimal sumTotalApprovedAmount();

    @Query(value = """
        SELECT DATE_FORMAT(p.approved_at, '%Y-%m-%d')  AS date,
               COALESCE(SUM(p.approved_amount - COALESCE(p.cancelled_amount, 0)), 0) AS revenue,
               COUNT(*)                                 AS order_count
        FROM payments p
        JOIN orders o ON o.id = p.order_id
        WHERE p.approved_at >= :from
          AND p.status IN ('CAPTURED', 'PARTIAL_REFUNDED')
          AND o.order_status <> 'CANCELLED'
        GROUP BY DATE_FORMAT(p.approved_at, '%Y-%m-%d')
        ORDER BY DATE_FORMAT(p.approved_at, '%Y-%m-%d') ASC
        """, nativeQuery = true)
    List<DailyRevenueProjection> findDailyRevenueSince(@Param("from") LocalDateTime from);
}
