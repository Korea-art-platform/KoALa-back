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
            throw new IllegalStateException(
                    "지원하지 않는 스키마 버전: " + event.schemaVersion()
                            + " (이 컨슈머는 v" + OrderCompletedEvent.CURRENT_SCHEMA_VERSION + " 까지)");
        }

        emailService.sendOrderConfirmEmail(OrderEventRelay.toEmailData(event));

        ack.acknowledge();
        log.info("주문 완료 메일 처리 완료: orderNo={}", event.orderNo());
    }
}
