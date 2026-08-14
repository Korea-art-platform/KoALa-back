package com.koala.koalaback.infra.slack;

import com.koala.koalaback.domain.order.event.OrderCompletedEvent;
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

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MM/dd HH:mm");

    public void notifyOrderCompleted(OrderCompletedEvent event) {
        try {
            SlackNotifier slack = slackProvider.getIfAvailable();
            if (slack == null) return;

            slack.send(buildMessage(event));
        } catch (Exception e) {
            log.warn("관리자 주문 알림 실패 (주문은 정상): orderNo={}, error={}",
                    event != null ? event.orderNo() : null, e.getMessage());
        }
    }

    String buildMessage(OrderCompletedEvent event) {
        StringBuilder sb = new StringBuilder();

        sb.append("🎨 *새 주문* ").append(money(event.totalAmount())).append('\n');
        sb.append("주문번호 `").append(event.orderNo()).append("`  ·  ")
          .append(ZonedDateTime.ofInstant(event.occurredAt(), KST).format(TIME_FORMAT)).append('\n');
        sb.append("주문자 ").append(nullSafe(event.ordererName())).append('\n');

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

        return sb.toString();
    }

    private String money(BigDecimal amount) {
        if (amount == null) return "-";
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
    }

    private String nullSafe(String value) {
        return value != null && !value.isBlank() ? value : "-";
    }
}
