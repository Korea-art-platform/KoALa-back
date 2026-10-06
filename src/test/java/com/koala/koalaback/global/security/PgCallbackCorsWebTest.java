package com.koala.koalaback.global.security;

import com.koala.koalaback.support.IntegrationTestSupport;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@DisplayName("PG 결과 수신 주소의 출처 제한")
class PgCallbackCorsWebTest extends IntegrationTestSupport {
    private static final String NICE_ORIGIN = "https://pay.nicepay.co.kr";

    @Autowired private WebApplicationContext context;
    @Autowired private Filter springSecurityFilterChain;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    @DisplayName("모바일 결제창이 나이스 출처로 결과를 POST 해도 CORS 에서 막지 않는다")
    void niceReturnAcceptsNiceOrigin() throws Exception {
        int status = mvc.perform(post("/api/v1/payments/nice/return")
                        .header("Origin", NICE_ORIGIN)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("authResultCode=9999&orderId=KL-CORS-TEST"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isNotEqualTo(403);
    }

    @Test
    @DisplayName("나이스 출처로 GET 으로 열려도 막지 않는다")
    void niceReturnGetAcceptsNiceOrigin() throws Exception {
        int status = mvc.perform(get("/api/v1/payments/nice/return").header("Origin", NICE_ORIGIN))
                .andReturn().getResponse().getStatus();

        assertThat(status).isNotEqualTo(403);
    }

    @Test
    @DisplayName("다른 일반 API 는 여전히 모르는 출처를 막는다")
    void otherApisStillRejectForeignOrigin() throws Exception {
        int status = mvc.perform(post("/api/v1/onsite/session")
                        .header("Origin", NICE_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"123456\"}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(403);
    }
}
