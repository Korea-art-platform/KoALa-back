package com.koala.koalaback.domain.settlement.repository;

import com.koala.koalaback.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 정산 원자료 집계.
 *
 * <p>{@link Order} 를 엔티티로 잡고 있지만 실제로는 집계 전용이다. 정산은 주문 도메인의
 * 관심사가 아니고, 주문 도메인도 정산을 알 필요가 없어 리포지토리를 따로 뒀다.
 *
 * <p>네이티브 쿼리를 쓰는 이유는 집계가 <b>세 테이블에 걸쳐 있고 비율 배분이 들어가기</b>
 * 때문이다. JPQL 로 쓰면 읽기가 더 어려워진다.
 */
public interface SettlementAggregationRepository extends JpaRepository<Order, Long> {

    /** 집계 결과 한 줄 — 인터페이스 프로젝션 (정산 도메인이 주문 DTO 에 얽히지 않게) */
    interface ArtistAmount {
        Long getArtistId();
        BigDecimal getAmount();
    }

    /**
     * 기간 내 <b>배송완료된</b> 주문의 작가별 매출.
     *
     * <h3>왜 결제일이 아니라 배송완료일인가</h3>
     * <p>결제 직후에 정산하면 배송 중 취소·반품이 난 건까지 이미 지급한 상태가 된다.
     * 물건이 고객에게 도착한 시점을 기준으로 잡아야 되돌릴 일이 줄어든다.
     *
     * <p>{@code artist_id} 가 없는 아이템은 제외한다 — 작가가 지워진 옛 데이터가 그렇다.
     * 넣어 두면 artistId 가 null 인 정산 행이 만들어진다.
     */
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

    /**
     * 기간 내 <b>승인된 반품</b>의 작가별 차감액.
     *
     * <h3>왜 비율로 나누는가</h3>
     * <p>반품은 주문 단위로 접수되고 환불 금액도 주문 단위 한 개다. 그런데 한 주문에
     * 여러 작가의 작품이 섞여 있을 수 있다. 그래서 환불액을 각 작가 아이템의
     * <b>금액 비중대로</b> 나눈다.
     *
     * <p>{@code NULLIF} 로 0 을 막는다. product_amount 가 0 이면 나눗셈이 터진다.
     *
     * <h3>왜 반품이 일어난 달에서 빼는가</h3>
     * <p>지난달 정산은 이미 확정·지급됐을 수 있다. 그 달을 소급해 고치면 이미 보낸 돈과
     * 장부가 어긋난다. 반품은 <b>승인된 달</b>의 차감으로 잡는다 — 회계에서 흔히 쓰는 방식이다.
     *
     * <p>교환(EXCHANGE)은 제외한다. 물건이 다시 나가므로 매출이 사라지지 않는다.
     */
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
