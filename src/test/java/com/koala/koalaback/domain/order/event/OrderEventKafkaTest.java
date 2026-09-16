package com.koala.koalaback.domain.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.koala.koalaback.global.config.KafkaConfig;
import com.koala.koalaback.infra.mail.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.timeout;

@SpringBootTest(classes = OrderEventKafkaTest.TestApp.class)
@EmbeddedKafka(partitions = 3, topics = {"order.completed", "order.completed.DLT"})
@TestPropertySource(properties = {
        "koala.events.kafka.enabled=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@DisplayName("주문 이벤트 Kafka 발행·수신")
class OrderEventKafkaTest {
    @Configuration
    @Import({KafkaConfig.class, OrderCompletedEmailConsumer.class})
    static class TestApp {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }
    }

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean private EmailService emailService;
    @MockitoBean private OrderConfirmDataFactory orderConfirmDataFactory;

    @Test
    @DisplayName("발행한 주문 완료 이벤트를 컨슈머가 받아 메일 발송을 호출한다")
    void publishedEvent_isConsumedAndTriggersEmail() {
        OrderCompletedEvent event = sampleEvent("ORD-1001", 1L);
        given(orderConfirmDataFactory.from(any())).willReturn(new EmailService.OrderConfirmData(
                "buyer@koala.test", "구매자", "ORD-1001", List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        kafkaTemplate.send(OrderCompletedEvent.TOPIC, event.partitionKey(), event);

        then(emailService).should(timeout(15_000))
                .sendOrderConfirmEmail(any(EmailService.OrderConfirmData.class));
    }

    @Test
    @DisplayName("같은 주문의 이벤트는 항상 같은 파티션에 들어간다 — 주문 단위 순서 보장")
    void sameOrder_alwaysGoesToSamePartition() throws Exception {
        OrderCompletedEvent first = sampleEvent("ORD-2002", 42L);
        OrderCompletedEvent second = sampleEvent("ORD-2002", 42L);

        int firstPartition = kafkaTemplate
                .send(OrderCompletedEvent.TOPIC, first.partitionKey(), first)
                .get().getRecordMetadata().partition();
        int secondPartition = kafkaTemplate
                .send(OrderCompletedEvent.TOPIC, second.partitionKey(), second)
                .get().getRecordMetadata().partition();

        org.assertj.core.api.Assertions.assertThat(firstPartition)
                .as("파티션 키가 orderId 이므로 같은 주문은 같은 파티션이어야 한다")
                .isEqualTo(secondPartition);
    }

    @Test
    @DisplayName("카프카로 나가는 이벤트에 주문자 이름·이메일이 없다")
    void eventCarriesNoOrdererPii() throws Exception {
        String json = new ObjectMapper().registerModule(new JavaTimeModule())
                .writeValueAsString(sampleEvent("ORD-3003", 7L));

        org.assertj.core.api.Assertions.assertThat(json)
                .doesNotContain("orderer")
                .doesNotContain("@");
    }

    private OrderCompletedEvent sampleEvent(String orderNo, Long orderId) {
        return OrderCompletedEvent.of(
                orderId, orderNo, 99L,
                BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(3_000),
                BigDecimal.valueOf(53_000),
                List.of(new OrderCompletedEvent.Item(
                        "SKU-1", "테스트 아트토이", "테스트 작가", 1, BigDecimal.valueOf(50_000))));
    }
}
