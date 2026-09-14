package com.koala.koalaback.infra.slack;

import com.koala.koalaback.domain.order.entity.Order;
import com.koala.koalaback.domain.order.event.OrderCompletedEvent;
import com.koala.koalaback.domain.order.repository.OrderRepository;
import com.koala.koalaback.domain.order.repository.OrderShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminOrderNotifier {
    private final ObjectProvider<SlackNotifier> slackProvider;
    private final OrderRepository orderRepository;
    private final OrderShipmentRepository shipmentRepository;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    static final String SHIPPING_UNAVAILABLE = "배송지를 불러오지 못했습니다 — 관리자 화면에서 확인";

    record Shipping(String recipientName, String recipientPhone,
                    String zipCode, String address1, String address2,
                    String deliveryRequest) {}

    public void notifyOrderCompleted(OrderCompletedEvent event) {
        try {
            SlackNotifier slack = slackProvider.getIfAvailable();
            if (slack == null) return;

            slack.send(buildMessage(event, findOrdererPhone(event), findShipping(event)));
        } catch (Exception e) {
            log.warn("관리자 주문 알림 실패 (주문은 정상): orderNo={}, error={}",
                    event != null ? event.orderNo() : null, e.getMessage());
        }
    }

    String findOrdererPhone(OrderCompletedEvent event) {
        try {
            return orderRepository.findById(event.orderId())
                    .map(Order::getOrdererPhone)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("주문 알림용 주문자 연락처 조회 실패 — 이메일만 보낸다: orderNo={}, error={}",
                    event.orderNo(), e.getMessage());
            return null;
        }
    }

    Shipping findShipping(OrderCompletedEvent event) {
        try {
            return shipmentRepository.findByOrderId(event.orderId())
                    .map(s -> new Shipping(s.getRecipientName(), s.getRecipientPhone(),
                            s.getZipCode(), s.getAddress1(), s.getAddress2(),
                            s.getDeliveryRequest()))
                    .orElse(null);
        } catch (Exception e) {
            log.warn("주문 알림용 배송지 조회 실패 — 배송지 없이 보낸다: orderNo={}, error={}",
                    event.orderNo(), e.getMessage());
            return null;
        }
    }

    String buildMessage(OrderCompletedEvent event) {
        return buildMessage(event, null, null);
    }

    String buildMessage(OrderCompletedEvent event, String ordererPhone, Shipping shipping) {
        StringBuilder sb = new StringBuilder();
        int totalQuantity = event.items().stream()
                .mapToInt(OrderCompletedEvent.Item::quantity)
                .sum();

        sb.append("🎨 *새 주문* ").append(money(event.totalAmount())).append("\n\n");

        sb.append("*주문*\n");
        sb.append("주문번호: `").append(event.orderNo()).append("`\n");
        sb.append("주문 날짜: ")
          .append(ZonedDateTime.ofInstant(event.occurredAt(), KST).format(DATE_FORMAT)).append('\n');
        sb.append("주문자: ").append(nullSafe(event.ordererName())).append('\n');
        sb.append("주문자 연락처: ").append(contact(event.ordererEmail(), ordererPhone)).append("\n\n");

        sb.append("*수령*\n");
        if (shipping == null) {
            sb.append(SHIPPING_UNAVAILABLE).append("\n\n");
        } else {
            sb.append("수령인: ").append(nullSafe(shipping.recipientName())).append('\n');
            sb.append("수령인 전화번호: ").append(nullSafe(displayPhone(shipping.recipientPhone()))).append('\n');
            sb.append("주소: ").append(address(shipping)).append('\n');
            sb.append("요청사항: ")
              .append(isBlank(shipping.deliveryRequest()) ? "없음" : shipping.deliveryRequest())
              .append("\n\n");
        }

        sb.append("*상품*\n");
        for (OrderCompletedEvent.Item item : event.items()) {
            sb.append("• ");

            if (item.artistName() != null && !item.artistName().isBlank()) {
                sb.append('[').append(item.artistName()).append("] ");
            }
            sb.append(item.skuName())
              .append(" × ").append(item.quantity())
              .append("  ").append(money(item.lineAmount()))
              .append('\n');
        }
        sb.append("수량: 총 ").append(totalQuantity).append("개\n");
        sb.append("금액: ").append(money(event.productAmount())).append('\n');
        sb.append("배송비: ").append(money(event.shippingAmount())).append('\n');
        sb.append("결제 합계: ").append(money(event.totalAmount()));

        return sb.toString();
    }

    static String displayPhone(String phone) {
        if (isBlank(phone)) return null;

        String d = phone.replaceAll("[^0-9+]", "");
        if (d.startsWith("+82")) {
            d = "0" + d.substring(3);
        } else if (d.startsWith("+")) {
            return phone;
        }

        if (d.startsWith("02")) {
            if (d.length() == 9) return d.substring(0, 2) + "-" + d.substring(2, 5) + "-" + d.substring(5);
            if (d.length() == 10) return d.substring(0, 2) + "-" + d.substring(2, 6) + "-" + d.substring(6);
        } else {
            if (d.length() == 10) return d.substring(0, 3) + "-" + d.substring(3, 6) + "-" + d.substring(6);
            if (d.length() == 11) return d.substring(0, 3) + "-" + d.substring(3, 7) + "-" + d.substring(7);
        }
        return d;
    }

    private String contact(String email, String phone) {
        String p = displayPhone(phone);
        if (isBlank(email) && p == null) return "-";
        if (isBlank(email)) return p;
        if (p == null) return email;
        return email + " · " + p;
    }

    private String address(Shipping s) {
        StringBuilder a = new StringBuilder();
        if (!isBlank(s.zipCode())) {
            a.append('(').append(s.zipCode()).append(") ");
        }
        a.append(nullSafe(s.address1()));
        if (!isBlank(s.address2())) {
            a.append(' ').append(s.address2());
        }
        return a.toString();
    }

    private String money(BigDecimal amount) {
        if (amount == null) return "-";
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nullSafe(String value) {
        return isBlank(value) ? "-" : value;
    }
}
