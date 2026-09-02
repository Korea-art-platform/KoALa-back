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

    /** 가입할 때 붙일 비회원 주문을 찾는다. */
    java.util.List<Order> findByOrdererEmailAndUserIsNull(String ordererEmail);

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

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

    List<Order> findByOrderStatusAndCreatedAtBefore(String orderStatus, LocalDateTime threshold);

    @Query("SELECT o FROM Order o JOIN FETCH o.shipment s "
            + "WHERE o.orderStatus = 'SHIPPED' "
            + "AND s.trackingNo IS NOT NULL "
            + "AND s.shippedAt >= :since")
    List<Order> findShippedWithTrackingSince(@Param("since") LocalDateTime since);
}
