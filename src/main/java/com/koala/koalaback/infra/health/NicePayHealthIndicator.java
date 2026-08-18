package com.koala.koalaback.infra.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component("nicepay")
@ConditionalOnProperty(name = "nicepay.enabled", havingValue = "true")
public class NicePayHealthIndicator implements HealthIndicator {
    static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);
    private static final int HTTPS_PORT = 443;

    private final String host;
    private final boolean sandbox;
    private final ExecutorService probeExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "nicepay-health");
                t.setDaemon(true);
                return t;
            });

    public NicePayHealthIndicator(@Value("${nicepay.api-base:https://api.nicepay.co.kr/v1}") String apiBase) {
        this.host = hostOf(apiBase);
        this.sandbox = apiBase.contains("sandbox");
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up()
                .withDetail("host", host)
                .withDetail("mode", sandbox ? "sandbox" : "production")
                .withDetail("advisory", "external system — excluded from readiness");

        Future<Boolean> probe = probeExecutor.submit(this::reachable);
        try {
            if (Boolean.TRUE.equals(probe.get(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))) {
                return builder.withDetail("reachable", true).build();
            }
            return unknown(builder, "connect failed");
        } catch (java.util.concurrent.TimeoutException e) {
            probe.cancel(true);
            return unknown(builder, "timed out after " + PROBE_TIMEOUT.toMillis() + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unknown(builder, "interrupted");
        } catch (Exception e) {
            return unknown(builder, e.getClass().getSimpleName());
        }
    }

    private Health unknown(Health.Builder builder, String reason) {
        log.info("[Health/NicePay] 결제사에 닿지 않는다 — 서비스 판정에는 쓰지 않는다: {}", reason);
        return builder.status(Status.UNKNOWN)
                .withDetail("reachable", false)
                .withDetail("reason", reason)
                .build();
    }

    private boolean reachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, HTTPS_PORT), (int) PROBE_TIMEOUT.toMillis());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String hostOf(String apiBase) {
        try {
            String host = URI.create(apiBase).getHost();
            return host != null ? host : apiBase;
        } catch (Exception e) {
            return apiBase;
        }
    }

    @PreDestroy
    void shutdown() {
        probeExecutor.shutdownNow();
    }
}
