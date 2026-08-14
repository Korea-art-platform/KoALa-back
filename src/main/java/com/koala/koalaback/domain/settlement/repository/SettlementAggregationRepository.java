package com.koala.koalaback.domain.settlement.repository;

import com.koala.koalaback.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SettlementAggregationRepository extends JpaRepository<Order, Long> {
    interface ArtistAmount {
        Long getArtistId();
        BigDecimal getAmount();
    }

    @Query(value = """
            SELECT oi.artist_id AS artistId,
                   SUM(oi.line_total_amount) AS amount
              FROM order_items oi
              JOIN orders o           ON o.id = oi.order_id
              JOIN order_shipments s  ON s.order_id = o.id
             WHERE o.order_status = 'DELIVERED'
               AND s.delivered_at >= :start
               AND s.delivered_at <  :end
               AND oi.artist_id IS NOT NULL
             GROUP BY oi.artist_id
            """, nativeQuery = true)
    List<ArtistAmount> sumDeliveredByArtist(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    @Query(value = """
            SELECT oi.artist_id AS artistId,
                   SUM(rr.refund_amount * oi.line_total_amount
                       / NULLIF(o.product_amount, 0)) AS amount
              FROM return_requests rr
              JOIN orders o      ON o.id = rr.order_id
              JOIN order_items oi ON oi.order_id = o.id
             WHERE rr.return_type = 'RETURN'
               AND rr.status IN ('APPROVED', 'COMPLETED')
               AND rr.refund_amount IS NOT NULL
               AND rr.processed_at >= :start
               AND rr.processed_at <  :end
               AND oi.artist_id IS NOT NULL
             GROUP BY oi.artist_id
            """, nativeQuery = true)
    List<ArtistAmount> sumRefundedByArtist(@Param("start") LocalDateTime start,
                                           @Param("end") LocalDateTime end);
}
