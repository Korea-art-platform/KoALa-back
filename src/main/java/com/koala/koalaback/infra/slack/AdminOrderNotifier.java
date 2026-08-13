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

/**
 * 주문이 들어오면 관리자에게 알린다.
 *
 * <p>3PL(물류 대행)을 쓰지 않기로 해서 포장·발송을 직접 한다.
 * 어드민을 계속 새로고침하지 않아도 주문을 놓치지 않게 하는 것이 목적이다.
 *
 * <p>메시지에 필요한 값은 전부 이벤트 페이로드에서 온다. 여기서 DB 를 다시 읽지 않는다 —
 * 이 시점은 트랜잭션 밖이라 지연로딩 컬렉션에 접근할 수 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminOrderNotifier {

    /** 슬랙이 꺼져 있으면 SlackNotifier 빈이 없다 — 선택적 주입 */
    private final ObjectProvider<SlackNotifier> slackProvider;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MM/dd HH:mm");

    /**
     * 주문 알림 발송.
     *
     * <p>여기서 예외가 나가면 <b>주문 확인 메일이 아예 나가지 않는다.</b>
     * 커밋 후 리스너에서 이 메서드가 메일보다 먼저 호출되기 때문이다.
     * 알림은 주문 처리의 결과이지 조건이 아니므로, 무슨 일이 있어도 삼킨다.
     */
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

    /** 슬랙 mrkdwn — 알림 목록에서 첫 줄만 보이므로 금액을 첫 줄에 둔다 */
    String buildMessage(OrderCompletedEvent event) {
        StringBuilder sb = new StringBuilder();

        sb.append("🎨 *새 주문* ").append(money(event.totalAmount())).append('\n');
        sb.append("주문번호 `").append(event.orderNo()).append("`  ·  ")
          .append(ZonedDateTime.ofInstant(event.occurredAt(), KST).format(TIME_FORMAT)).append('\n');
        sb.append("주문자 ").append(nullSafe(event.ordererName())).append('\n');

        for (OrderCompletedEvent.Item item : event.items()) {
            sb.append("• ");
            // 작가별로 발송 주체가 갈리므로 작가명을 상품명 앞에 둔다
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
