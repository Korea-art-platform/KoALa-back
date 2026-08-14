package com.koala.koalaback.global.util;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

public final class IpResolverUtil {
    private static final Set<String> TRUSTED_PREFIXES = Set.of(
            "127.", "10.",
            "172.16.", "172.17.", "172.18.", "172.19.", "172.20.",
            "172.21.", "172.22.", "172.23.", "172.24.", "172.25.",
            "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31.",
            "192.168."
    );
    private static final Set<String> TRUSTED_EXACT = Set.of("::1", "0:0:0:0:0:0:0:1");

    private IpResolverUtil() {}

    public static String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        if (isTrustedProxy(remoteAddr)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return xRealIp.trim();
            }
        }

        return remoteAddr;
    }

    private static boolean isTrustedProxy(String addr) {
        if (addr == null) return false;
        if (TRUSTED_EXACT.contains(addr)) return true;
        return TRUSTED_PREFIXES.stream().anyMatch(addr::startsWith);
    }
}
