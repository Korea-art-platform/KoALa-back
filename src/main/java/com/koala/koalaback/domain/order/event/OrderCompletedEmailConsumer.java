package com.koala.koalaback.domain.order.event;

import com.koala.koalaback.global.config.KafkaConfig;
import com.koala.koalaback.infra.mail.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 주문 완료 메일 컨슈머 — {@code order.completed} → 주문 확인 메일.
 *
 * <p>기존에는 결제 승인 트랜잭션 안에서 {@code @Async} 로 발송했다.
 * 그 구조에서는 메일 준비가 실패하면 결제 트랜잭션에 영향이 갈 수 있었고,
 * 실패해도 재시도할 방법이 없었다. 컨슈머로 옮기면 발송 실패가
 * 주문·결제와 완전히 분리되고, 재시도와 DLT 로 실패 건을 추적할 수 있다.
 *
 * <p>재시도/DLT 는 {@link KafkaConfig#kafkaListenerContainerFactory} 의
 * {@code DefaultErrorHandler} 가 담당한다 — 여기서 예외를 삼키면 안 된다.
 * 삼키면 실패가 성공으로 커밋되어 DLT 로도 가지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "koala.events.kafka", name = "enabled", havingValue = "true")
public class OrderCompletedEmailConsumer {

    private final EmailService emailService;

    @KafkaListener(
            topics = OrderCompletedEvent.TOPIC,
            groupId = KafkaConfig.GROUP_ORDER_EMAIL,
            containerFactory = "kafkaListenerContainerFactory")
    public void handle(@Payload OrderCompletedEvent event,
                       @Header(name = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
                       @Header(name = KafkaHeaders.OFFSET, required = false) Long offset,
                       Acknowledgment ack) {

        log.info("주문 완료 이벤트 수신: orderNo={}, eventId={}, partition={}, offset={}",
                event.orderNo(), event.eventId(), partition, offset);

        if (event.schemaVersion() > OrderCompletedEvent.CURRENT_SCHEMA_VERSION) {
            // 모르는 상위 버전 — 잘못 처리하느니 DLT 로 보내 사람이 보게 한다
            throw new IllegalStateException(
                    "지원하지 않는 스키마 버전: " + event.schemaVersion()
                            + " (이 컨슈머는 v" + OrderCompletedEvent.CURRENT_SCHEMA_VERSION + " 까지)");
        }

        emailService.sendOrderConfirmEmail(OrderEventRelay.toEmailData(event));

        // 처리 성공 후에만 오프셋 커밋 — 앞서 커밋하면 실패 건이 유실된다
        ack.acknowledge();
        log.info("주문 완료 메일 처리 완료: orderNo={}", event.orderNo());
    }
}
