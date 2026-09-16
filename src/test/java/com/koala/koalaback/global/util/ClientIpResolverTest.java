package com.koala.koalaback.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("클라이언트 IP — 앞에서 보낸 X-Forwarded-For 를 믿지 않는다")
class ClientIpResolverTest {

    private MockHttpServletRequest request(String remoteAddr, String xff) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(remoteAddr);
        if (xff != null) req.addHeader("X-Forwarded-For", xff);
        return req;
    }

    @Test
    @DisplayName("프록시를 거치지 않으면 접속 주소를 그대로 쓴다")
    void direct() {
        ClientIpResolver resolver = new ClientIpResolver(1);

        assertThat(resolver.resolve(request("203.0.113.9", "1.2.3.4")))
                .isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("앞단 프록시가 붙인 값을 걷어내고 그 앞의 값을 쓴다")
    void behindOneTrustedHop() {
        ClientIpResolver resolver = new ClientIpResolver(1);

        assertThat(resolver.resolve(request("127.0.0.1", "198.51.100.7, 70.132.0.1")))
                .isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("공격자가 목록을 길게 채워 보내도 앞자리 값을 쓰지 않는다")
    void ignoresSpoofedPrefix() {
        ClientIpResolver resolver = new ClientIpResolver(1);

        String spoofed = "1.1.1.1, 2.2.2.2, 198.51.100.7, 70.132.0.1";

        assertThat(resolver.resolve(request("10.0.0.5", spoofed)))
                .isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("프록시가 없다고 설정하면 맨 오른쪽 값을 쓴다")
    void noTrustedHop() {
        ClientIpResolver resolver = new ClientIpResolver(0);

        assertThat(resolver.resolve(request("127.0.0.1", "1.1.1.1, 198.51.100.7")))
                .isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("헤더가 없으면 접속 주소를 쓴다")
    void noHeader() {
        ClientIpResolver resolver = new ClientIpResolver(1);

        assertThat(resolver.resolve(request("10.0.0.5", null)))
                .isEqualTo("10.0.0.5");
    }

    @Test
    @DisplayName("값이 하나뿐이면 그 값을 쓴다")
    void singleEntry() {
        ClientIpResolver resolver = new ClientIpResolver(1);

        assertThat(resolver.resolve(request("10.0.0.5", "198.51.100.7")))
                .isEqualTo("198.51.100.7");
    }
}
