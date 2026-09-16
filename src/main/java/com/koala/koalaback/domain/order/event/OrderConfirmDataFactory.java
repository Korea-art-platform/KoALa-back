package com.koala.koalaback.domain.order.event;

import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.infra.mail.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConfirmDataFactory {
    private final OrderRepository orderRepository;

    public EmailService.OrderConfirmData from(OrderCompletedEvent event) {
        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.warn("주문을 찾지 못해 주문 완료 메일을 만들 수 없다: orderNo={}", event.orderNo());
            return null;
        }

        return new EmailService.OrderConfirmData(
                order.getOrdererEmail(),
                order.getOrdererName(),
                event.orderNo(),
                event.items().stream()
                        .map(i -> new EmailService.OrderConfirmData.ItemData(
                                i.skuName(), i.quantity(), i.lineAmount()))
                        .toList(),
                event.productAmount(),
                event.shippingAmount(),
                event.totalAmount());
    }
}
