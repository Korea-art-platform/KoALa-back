package com.koala.koalaback.infra.slack;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;

/**
 * 슬랙 Incoming Webhook 알림.
 *
 * <p><b>Incoming Webhook</b> — 슬랙이 채널마다 발급하는 URL. 그 URL 에 JSON 을 POST 하면
 * 해당 채널에 메시지가 올라간다. 토큰·OAuth 가 필요 없어 서버에서 한 방향으로
 * 알림만 보낼 때 쓴다.
 *
 * <h3>URL 은 비밀값이다</h3>
 * <p>URL 을 아는 사람은 누구나 그 채널에 글을 쓸 수 있다. 그래서 코드·저장소에 넣지 않고
 * 운영 서버의 {@code /opt/koala/.env} 로만 주입한다. 값이 없으면 이 빈 자체가 생성되지 않는다.
 *
 * <h3>알림 실패가 주문을 막지 않는다</h3>
 * <p>이 시점에는 결제·주문이 이미 커밋된 뒤다. 여기서 예외를 던지면 되돌릴 것도 없이
 * 고객 화면만 깨진다. 그래서 모든 예외를 삼키고 로그만 남긴다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "koala.slack.enabled", havingValue = "true")
public class SlackNotifier {

    private final RestTemplate restTemplate;
    private final String webhookUrl;

    public SlackNotifier(@Value("${koala.slack.webhook-url:}") String webhookUrl,
                         @Value("${koala.slack.timeout-ms:3000}") long timeoutMs) {
        this.webhookUrl = webhookUrl;

        // 결제 응답 경로에서 호출되므로 PG용 RestTemplate(읽기 10초)을 그대로 쓰지 않는다.
        // 슬랙이 느릴 때 고객의 결제 완료 화면이 그만큼 늦어지면 안 된다.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restTemplate = new RestTemplate(factory);

        if (webhookUrl.isBlank()) {
            log.warn("koala.slack.enabled=true 이지만 webhook-url 이 비어 있다 — 슬랙 알림이 나가지 않는다");
        }
    }

    /**
     * 슬랙 채널에 메시지를 보낸다.
     *
     * <p>{@code @Async} — 호출한 스레드를 붙잡지 않는다. 알림은 주문 처리의 결과지
     * 조건이 아니므로, 슬랙이 느리다고 고객을 기다리게 할 이유가 없다.
     *
     * @param text 알림 본문 (슬랙 mrkdwn 문법 사용 가능)
     */
    @Async
    public void send(String text) {
        if (webhookUrl.isBlank()) return;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            restTemplate.postForEntity(
                    webhookUrl,
                    new HttpEntity<>(Map.of("text", text), headers),
                    String.class);

        } catch (Exception e) {
            // URL 을 로그에 남기지 않는다 — 로그를 보는 사람이 곧 채널 쓰기 권한을 갖게 된다
            log.warn("슬랙 알림 발송 실패: {}", e.getMessage());
        }
    }
}
