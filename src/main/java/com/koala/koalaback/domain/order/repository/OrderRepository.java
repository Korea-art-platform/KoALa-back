package com.koala.koalaback.domain.order.repository;

import com.koala.koalaback.domain.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    Optional<Order> findByOrderNoAndUserId(String orderNo, Long userId);

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 회원ID / 주문자명 / 전화번호 중 하나라도 일치하면 반환 (OR 조건)
     * - 파라미터가 null 이면 해당 조건을 무시 (= 전체 매칭)
     * - 하나라도 non-null 이면 해당 항목으로만 필터링
     */
    @Query("SELECT o FROM Order o WHERE " +
           "(:userId IS NULL AND :name IS NULL AND :phone IS NULL) OR " +
           "(:userId IS NOT NULL AND o.user.id = :userId) OR " +
           "(:name  IS NOT NULL AND o.ordererName  LIKE %:name%) OR " +
           "(:phone IS NOT NULL AND o.ordererPhone LIKE %:phone%) " +
           "ORDER BY o.createdAt DESC")
    Page<Order> searchOrders(
            @Param("userId") Long userId,
            @Param("name")   String name,
            @Param("phone")  String phone,
            Pageable pageable);
    long countByCreatedAtAfter(LocalDateTime dateTime);
    long countByOrderStatus(String orderStatus);

    /** 특정 상태이면서 생성 시각이 기준 이전인 주문 (미결제 만료 자동취소용) */
    List<Order> findByOrderStatusAndCreatedAtBefore(String orderStatus, LocalDateTime threshold);

    /**
     * 배송 중이면서 운송장이 등록된 주문 (배송완료 자동 추적용).
     *
     * <p>{@code shippedAt} 으로 하한을 두는 이유는, 조회에 잡히지 않는 운송장이 하나 생기면
     * 그 주문이 <b>영원히</b> 폴링 대상으로 남기 때문이다. 오래된 건은 손으로 정리한다.
     */
    @Query("SELECT o FROM Order o JOIN FETCH o.shipment s "
            + "WHERE o.orderStatus = 'SHIPPED' "
            + "AND s.trackingNo IS NOT NULL "
            + "AND s.shippedAt >= :since")
    List<Order> findShippedWithTrackingSince(@Param("since") LocalDateTime since);
}