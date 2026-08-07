package com.koala.koalaback.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 설정 — {@code koala.events.kafka.enabled=true} 일 때만 활성화된다.
 *
 * <p><b>왜 조건부인가:</b> 운영(EC2)은 jar + systemd 로만 뜨고 브로커가 없다.
 * 무조건 활성화하면 기동 시 브로커를 찾지 못해 리스너가 계속 재연결을 시도하고,
 * 메일 같은 후처리가 통째로 멎는다. 브로커를 배포한 뒤 플래그를 켜는 순서로 간다.
 * 꺼져 있는 동안에는 {@code OrderEventRelay} 가 커밋 후 직접 처리로 대체한다.
 */
@Slf4j
@Configuration
@EnableKafka
@ConditionalOnProperty(prefix = "koala.events.kafka", name = "enabled", havingValue = "true")
public class KafkaConfig {

    /** 컨슈머 그룹 — 소비 목적별로 분리해야 한 쪽이 지연돼도 다른 쪽이 막히지 않는다 */
    public static final String GROUP_ORDER_EMAIL = "koala.order-email";

    /**
     * 논리 타입명 ↔ 클래스 매핑.
     *
     * <p>메시지 헤더에 클래스명(FQCN) 대신 {@code order.completed} 같은 논리 이름을 싣는다.
     * 클래스명을 실으면 발행측 패키지를 옮기는 것만으로 컨슈머 역직렬화가 깨지고,
     * 서로 다른 배포 시점의 프로듀서/컨슈머가 공존할 수 없다.
     */
    private static final String TYPE_MAPPINGS =
            "order.completed:com.koala.koalaback.domain.order.event.OrderCompletedEvent,"
          + "order.cancelled:com.koala.koalaback.domain.order.event.OrderCancelledEvent";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // ── 토픽 ──────────────────────────────────────────────
    // 로컬은 파티션 3 / 복제 1. 파티션 키가 orderId 라 같은 주문은 항상 같은 파티션에 간다.
    // (운영에서는 복제 계수를 3 이상으로 올릴 것)

    @Bean
    public NewTopic orderCompletedTopic() {
        return TopicBuilder.name("order.completed").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderCancelledTopic() {
        return TopicBuilder.name("order.cancelled").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderCompletedDltTopic() {
        return TopicBuilder.name("order.completed.DLT").partitions(3).replicas(1).build();
    }

    // ── Producer ──────────────────────────────────────────

    @Bean
    public ProducerFactory<String, Object> producerFactory(ObjectMapper objectMapper) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // acks=all + 멱등 프로듀서 — 브로커 장애 시 유실/중복 발행을 줄인다
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        // 타입 헤더에 클래스명 대신 논리 이름(order.completed)을 싣는다.
        // 클래스명을 그대로 실으면 컨슈머가 발행측 패키지 구조에 묶여, 리팩터링만 해도 역직렬화가 깨진다.
        props.put(JsonSerializer.TYPE_MAPPINGS, TYPE_MAPPINGS);

        JsonSerializer<Object> valueSerializer = new JsonSerializer<>(objectMapper);
        valueSerializer.configure(props, false);

        return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), valueSerializer);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // ── Consumer ──────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, Object> consumerFactory(ObjectMapper objectMapper) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // 자동 커밋 끄고 리스너가 처리 성공 후 커밋 — 처리 전에 커밋되면 유실된다
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // 역직렬화 실패가 무한 재시도 루프(poison pill)가 되지 않도록 ErrorHandlingDeserializer 로 감싼다.
        // 감싸지 않으면 깨진 메시지 하나가 파티션을 영구히 막는다.
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        // 발행측과 같은 논리 타입 매핑 — 클래스명이 아니라 이름으로 매칭한다
        props.put(JsonDeserializer.TYPE_MAPPINGS, TYPE_MAPPINGS);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.koala.koalaback.domain.order.event");
        // 모르는 필드는 무시 — 발행측이 필드를 추가해도 구버전 컨슈머가 깨지지 않는다(추가 호환)
        props.put(JsonDeserializer.REMOVE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * 리스너 컨테이너 — 재시도 후 DLT 로 보낸다.
     *
     * <p>2초 간격 3회까지 재시도하고(일시적 SMTP 장애 등 회복 가능한 실패 대비),
     * 그래도 실패하면 원본 토픽명 + {@code .DLT} 로 보내고 오프셋을 넘긴다.
     * DLT 가 없으면 실패 메시지가 파티션을 막아 뒤 메시지가 전부 밀린다.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // 수동 커밋 — 리스너가 처리에 성공한 뒤에만 오프셋을 넘긴다.
        // (이 설정이 있어야 리스너 파라미터로 Acknowledgment 가 주입된다)
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(recoverer, new FixedBackOff(2_000L, 3L));
        // 역직렬화 실패는 재시도해도 절대 성공하지 않는다 — 즉시 DLT 로 보낸다
        errorHandler.addNotRetryableExceptions(
                org.springframework.kafka.support.serializer.DeserializationException.class);
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
