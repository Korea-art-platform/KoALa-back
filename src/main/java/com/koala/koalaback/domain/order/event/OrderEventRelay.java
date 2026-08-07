package com.koala.koalaback.domain.order.event;

import com.koala.koalaback.infra.mail.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 이벤트를 <b>DB 커밋 이후</b>에 외부로 내보내는 릴레이.
 *
 * <h3>왜 {@code AFTER_COMMIT} 인가</h3>
 * <p>트랜잭션 안에서 발행하면 그 뒤 롤백이 났을 때 DB 에는 없는 주문에 대한
 * 이벤트가 이미 나가 있는 "유령 이벤트"가 된다. 메일이라면 결제되지 않은 주문의
 * 완료 메일이 고객에게 날아간다. {@code AFTER_COMMIT} 은 커밋이 확정된 뒤에만 호출되므로
 * 이 문제가 생기지 않는다.
 *
 * <p>반대급부로 <b>커밋은 됐는데 발행이 실패</b>할 수는 있다(적어도 한 번 보장이 아님).
 * 지금 규모에서는 발행 실패를 에러 로그로 남기는 선에서 감수한다.
 * 유실이 허용되지 않게 되면 outbox 테이블(같은 트랜잭션에 이벤트를 저장하고
 * 별도 프로세스가 릴레이)로 올려야 한다.
 *
 * <h3>Kafka 가 꺼져 있을 때</h3>
 * <p>운영에는 아직 브로커가 없다. 플래그가 꺼져 있으면 Kafka 로 보내지 않고
 * 커밋 후 직접 메일을 보낸다 — 기존 동작과 같되, 트랜잭션 <b>밖</b>이라는 점만 다르다.
 * 브로커를 배포하고 플래그를 켜면 이 fallback 은 자동으로 비활성화된다.
 */
@Slf4j
@Component
public class OrderEventRelay {

    /** Kafka 가 비활성이면 이 빈이 없다 — ObjectProvider 로 선택적 주입 */
    private final ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider;
    private final EmailService emailService;
    private final boolean kafkaEnabled;

    public OrderEventRelay(ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider,
                           EmailService emailService,
                           @Value("${koala.events.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplateProvider = kafkaTemplateProvider;
        this.emailService = emailService;
        this.kafkaEnabled = kafkaEnabled;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCompleted(OrderCompletedEvent event) {
        if (!kafkaEnabled) {
            sendOrderConfirmEmailDirectly(event);
            return;
        }
        publish(OrderCompletedEvent.TOPIC, event.partitionKey(), event, event.orderNo());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelled(OrderCancelledEvent event) {
        if (!kafkaEnabled) {
            // 취소 이벤트는 아직 소비처가 없다 — 꺼져 있으면 할 일이 없다
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
                        // 커밋은 이미 끝났다 — 되돌릴 수 없으므로 남겨서 추적한다
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

    /** Kafka 미사용 환경용 fallback — 커밋 후 직접 발송 */
    private void sendOrderConfirmEmailDirectly(OrderCompletedEvent event) {
        try {
            emailService.sendOrderConfirmEmail(toEmailData(event));
        } catch (Exception e) {
            log.warn("주문 완료 메일 발송 실패 (주문은 정상): orderNo={}, error={}",
                    event.orderNo(), e.getMessage());
        }
    }

    /** 이벤트 → 메일 데이터 변환 (Kafka 컨슈머도 동일하게 사용) */
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
