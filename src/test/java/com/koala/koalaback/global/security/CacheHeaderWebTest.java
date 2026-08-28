package com.koala.koalaback.global.security;

import com.koala.koalaback.support.IntegrationTestSupport;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * 실제 Security 필터 체인을 통과했을 때 캐시 헤더가 살아남는지 본다.
 *
 * 단위 테스트(PublicCacheHeaderFilterTest)는 필터 하나만 본다. 진짜 실패는
 * Security 가 응답 커밋 직전에 no-store 로 덮을 때 나는데, 그건 전체 체인을
 * 태워야 잡힌다. 그래서 springSecurityFilterChain 을 그대로 물려 검증한다.
 */
@DisplayName("공개 API 캐시 헤더 (Security 체인 통과)")
class CacheHeaderWebTest extends IntegrationTestSupport {

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
    @DisplayName("배너 응답에 캐시 헤더가 살아남고 no-store 는 없다")
    void bannersCached() throws Exception {
        mvc.perform(get("/api/v1/banners").param("bannerType", "MAIN"))
                .andExpect(header().string("Cache-Control", containsString("max-age=60")))
                .andExpect(header().string("Cache-Control", not(containsString("no-store"))));
    }

    @Test
    @DisplayName("상품 목록도 캐시된다")
    void skusCached() throws Exception {
        mvc.perform(get("/api/v1/skus"))
                .andExpect(header().string("Cache-Control", containsString("max-age=60")));
    }

    @Test
    @DisplayName("보호 경로(내 정보)는 캐시되지 않는다")
    void meNotCached() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(header().string("Cache-Control",
                        anyOf(nullValue(), not(containsString("max-age=60")))));
    }
}
