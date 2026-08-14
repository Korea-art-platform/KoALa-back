package com.koala.koalaback.infra.slack;

import com.koala.koalaback.domain.order.event.OrderCompletedEvent;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

@DisplayName("슬랙 알림 실패 경로")
class SlackFailurePathTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    private String serverReturning(int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/hook", exchange -> {
            exchange.sendResponseHeaders(status, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("no".getBytes());
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
    }

    private String hangingServer(AtomicInteger hits) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/hook", exchange -> {
            hits.incrementAndGet();
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
    }

    @Nested
    @DisplayName("웹훅이 정상이 아닐 때")
    class WebhookProblems {
        @Test
        @DisplayName("500 을 돌려줘도 예외가 새어 나가지 않는다")
        void serverErrorIsSwallowed() throws IOException {
            SlackNotifier notifier = new SlackNotifier(serverReturning(500), 3000);

            assertThatCode(() -> notifier.send("테스트")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("404 를 돌려줘도 예외가 새어 나가지 않는다 — URL 이 틀린 경우")
        void notFoundIsSwallowed() throws IOException {
            SlackNotifier notifier = new SlackNotifier(serverReturning(404), 3000);

            assertThatCode(() -> notifier.send("테스트")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("서버가 아예 없어도 예외가 새어 나가지 않는다")
        void connectionRefusedIsSwallowed() {
            SlackNotifier notifier = new SlackNotifier("http://127.0.0.1:1/hook", 1000);

            assertThatCode(() -> notifier.send("테스트")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("응답이 없으면 타임아웃으로 끊고 넘어간다 — 결제 화면이 기다리면 안 된다")
        void hangingServerTimesOut() throws IOException {
            AtomicInteger hits = new AtomicInteger();
            SlackNotifier notifier = new SlackNotifier(hangingServer(hits), 500);

            long start = System.currentTimeMillis();
            assertThatCode(() -> notifier.send("테스트")).doesNotThrowAnyException();
            long elapsed = System.currentTimeMillis() - start;

            assertThat(hits.get()).as("요청은 실제로 서버에 닿았다").isEqualTo(1);
            assertThat(elapsed)
                    .as("타임아웃(500ms)에 걸려 끊겨야 한다. 서버는 5초를 붙잡고 있다")
                    .isLessThan(3_000);
        }

        @Test
        @DisplayName("URL 이 비어 있으면 아무것도 하지 않는다")
        void blankUrlDoesNothing() {
            SlackNotifier notifier = new SlackNotifier("", 3000);

            assertThatCode(() -> notifier.send("테스트")).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("알림이 주문을 막지 않는다")
    class DoesNotBlockOrder {
        @Test
        @DisplayName("슬랙이 꺼져 있으면 조용히 넘어간다")
        void disabledSlackIsNoop() {
            @SuppressWarnings("unchecked")
            ObjectProvider<SlackNotifier> empty = mock(ObjectProvider.class);

            AdminOrderNotifier notifier = new AdminOrderNotifier(empty);

            assertThatCode(() -> notifier.notifyOrderCompleted(sampleEvent()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("이벤트가 망가져 있어도 주문 흐름을 끊지 않는다")
        void malformedEventDoesNotBreakFlow() {
            @SuppressWarnings("unchecked")
            ObjectProvider<SlackNotifier> provider = mock(ObjectProvider.class);
            org.mockito.BDDMockito.given(provider.getIfAvailable())
                    .willReturn(new SlackNotifier("http://127.0.0.1:1/hook", 500));

            AdminOrderNotifier notifier = new AdminOrderNotifier(provider);

            OrderCompletedEvent broken = new OrderCompletedEvent(
                    "evt", "order.completed", 1, java.time.Instant.now(),
                    1L, "ORD-1", 1L, null, null, null, null, null, null);

            assertThatCode(() -> notifier.notifyOrderCompleted(broken))
                    .doesNotThrowAnyException();
        }
    }

    private OrderCompletedEvent sampleEvent() {
        return OrderCompletedEvent.of(
                1L, "ORD-1", 1L, "홍길동", "a@b.c",
                new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("10000"),
                List.of(new OrderCompletedEvent.Item("SKU-1", "작품", "작가", 1, new BigDecimal("10000"))));
    }
}
