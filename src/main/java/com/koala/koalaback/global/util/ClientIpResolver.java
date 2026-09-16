package com.koala.koalaback.global.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 요청을 보낸 실제 클라이언트 IP.
 *
 * X-Forwarded-For 는 클라이언트가 먼저 채워 보낼 수 있다. 맨 앞 값을 믿으면
 * 속도 제한과 어드민 IP 허용목록을 마음대로 통과할 수 있어, 우리 쪽 프록시가
 * 붙인 오른쪽 값부터 센다.
 *
 * trustedHops 는 우리 프록시 앞단에 있는, 값을 덧붙이는 프록시 수다.
 * CloudFront → nginx → 앱 구성이면 1 이다. 구성이 바뀌면 이 값을 바꿔야 한다.
 */
@Component
public class ClientIpResolver {
    private static final Set<String> TRUSTED_PREFIXES = Set.of(
            "127.", "10.",
            "172.16.", "172.17.", "172.18.", "172.19.", "172.20.",
            "172.21.", "172.22.", "172.23.", "172.24.", "172.25.",
            "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31.",
            "192.168."
    );
    private static final Set<String> TRUSTED_EXACT = Set.of("::1", "0:0:0:0:0:0:0:1");

    private final int trustedHops;

    public ClientIpResolver(@Value("${app.forwarded.trusted-hops:1}") int trustedHops) {
        this.trustedHops = Math.max(0, trustedHops);
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        List<String> chain = parseChain(request.getHeader("X-Forwarded-For"));
        if (!chain.isEmpty()) {
            int index = Math.max(0, chain.size() - 1 - trustedHops);
            return chain.get(index);
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        return remoteAddr;
    }

    private List<String> parseChain(String header) {
        if (header == null || header.isBlank()) return List.of();
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public boolean isTrustedProxy(String addr) {
        if (addr == null) return false;
        if (TRUSTED_EXACT.contains(addr)) return true;
        return TRUSTED_PREFIXES.stream().anyMatch(addr::startsWith);
    }
}
