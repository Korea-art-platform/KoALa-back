package com.koala.koalaback.domain.order.repository;

import com.koala.koalaback.domain.order.entity.OrderShipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderShipmentRepository extends JpaRepository<OrderShipment, Long> {
    Optional<OrderShipment> findByOrderId(Long orderId);

    Optional<OrderShipment> findByCarrierCodeAndTrackingNo(String carrierCode, String trackingNo);
}
