package com.koala.koalaback.domain.order.event;

import com.koala.koalaback.infra.mail.EmailService;
import com.koala.koalaback.infra.slack.AdminOrderNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class OrderEventRelay {
    private final ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider;
    private final EmailService emailService;
    private final AdminOrderNotifier adminOrderNotifier;
    private final boolean kafkaEnabled;

    public OrderEventRelay(ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider,
                           EmailService emailService,
                           AdminOrderNotifier adminOrderNotifier,
                           @Value("${koala.events.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplateProvider = kafkaTemplateProvider;
        this.emailService = emailService;
        this.adminOrderNotifier = adminOrderNotifier;
        this.kafkaEnabled = kafkaEnabled;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCompleted(OrderCompletedEvent event) {
        adminOrderNotifier.notifyOrderCompleted(event);

        if (!kafkaEnabled) {
            sendOrderConfirmEmailDirectly(event);
            return;
        }
        publish(OrderCompletedEvent.TOPIC, event.partitionKey(), event, event.orderNo());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        if (!kafkaEnabled) {
            log.debug("Kafka 비활성 — 주문 취소 이벤트 발행 생략: orderNo={}", event.orderNo());
            return;
        }
        publish(OrderCancelledEvent.TOPIC, event.partitionKey(), event, event.orderNo());
    }

    private void publish(String topic, String key, Object event, String orderNo) {
        KafkaTemplate<String, Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            log.error("Kafka 활성 설정이지만 KafkaTemplate 이 없다 — 발행 생략: topic={}, orderNo={}",
                    topic, orderNo);
            return;
        }
        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("이벤트 발행 실패: topic={}, orderNo={}, error={}",
                                topic, orderNo, ex.getMessage(), ex);
                    } else {
                        log.info("이벤트 발행: topic={}, partition={}, offset={}, orderNo={}",
                                topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset(),
                                orderNo);
                    }
                });
    }

    private void sendOrderConfirmEmailDirectly(OrderCompletedEvent event) {
        try {
            emailService.sendOrderConfirmEmail(toEmailData(event));
        } catch (Exception e) {
            log.warn("주문 완료 메일 발송 실패 (주문은 정상): orderNo={}, error={}",
                    event.orderNo(), e.getMessage());
        }
    }

    public static EmailService.OrderConfirmData toEmailData(OrderCompletedEvent event) {
        return new EmailService.OrderConfirmData(
                event.ordererEmail(),
                event.ordererName(),
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
