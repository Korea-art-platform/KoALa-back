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

    /** 가입할 때 붙일 비회원 주문을 찾는다. 이메일은 암호화돼 있어 해시로 찾는다. */
    java.util.List<Order> findByOrdererEmailHashAndUserIsNull(String ordererEmailHash);

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 주문자 정보가 암호화돼 부분 일치 검색이 되지 않는다.
     * 전화번호는 전체(해시) 또는 뒷자리 4자리로만 찾는다.
     */
    @Query("SELECT o FROM Order o WHERE " +
           "(:userId IS NULL AND :phoneHash IS NULL AND :phoneLast4 IS NULL) OR " +
           "(:userId IS NOT NULL AND o.user.id = :userId) OR " +
           "(:phoneHash IS NOT NULL AND o.ordererPhoneHash = :phoneHash) OR " +
           "(:phoneLast4 IS NOT NULL AND o.ordererPhoneLast4 = :phoneLast4) " +
           "ORDER BY o.createdAt DESC")
    Page<Order> searchOrders(
            @Param("userId")     Long userId,
            @Param("phoneHash")  String phoneHash,
            @Param("phoneLast4") String phoneLast4,
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
