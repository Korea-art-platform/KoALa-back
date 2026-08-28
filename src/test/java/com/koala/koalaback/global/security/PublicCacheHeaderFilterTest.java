package com.koala.koalaback.global.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("공개 API 캐시 헤더")
class PublicCacheHeaderFilterTest {

    private final PublicCacheHeaderFilter filter = new PublicCacheHeaderFilter();

    private String cacheHeaderFor(String method, String uri, Cookie[] cookies, String auth, int status)
            throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRequestURI(uri);
        if (cookies != null) req.setCookies(cookies);
        if (auth != null) req.addHeader("Authorization", auth);

        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest rq, jakarta.servlet.ServletResponse rs) {
                ((MockHttpServletResponse) rs).setStatus(status);
            }
        };
        filter.doFilterInternal(req, res, chain);
        return res.getHeader("Cache-Control");
    }

    private String get(String uri) throws Exception {
        return cacheHeaderFor("GET", uri, null, null, 200);
    }

    @Test
    @DisplayName("공개 목록은 캐시된다")
    void publicListsCached() throws Exception {
        for (String uri : new String[]{
                "/api/v1/banners",
                "/api/v1/artists",
                "/api/v1/skus",
                "/api/v1/skus/genre-counts",
                "/api/v1/skus/main-category-counts",
                "/api/v1/skus/ABC123",            // 상품 상세
                "/api/v1/artists/A1/skus",         // 작가별 작품
        }) {
            assertThat(get(uri))
                    .as(uri)
                    .isEqualTo("public, max-age=60, stale-while-revalidate=60");
        }
    }

    @Test
    @DisplayName("사람마다 다른 응답은 캐시하지 않는다")
    void perUserNotCached() throws Exception {
        for (String uri : new String[]{
                "/api/v1/users/me",
                "/api/v1/cart",
                "/api/v1/orders",
                "/api/v1/artists/A1",              // 상세 — 팔로우 여부가 섞인다
                "/api/v1/artists/A1/following",
                "/api/v1/skus/ABC123/reviews",     // 하위 경로
        }) {
            assertThat(get(uri)).as(uri).isNull();
        }
    }

    @Test
    @DisplayName("인증이 붙은 요청은 공개 경로라도 캐시하지 않는다")
    void authedRequestNotCached() throws Exception {
        assertThat(cacheHeaderFor("GET", "/api/v1/skus", null, "Bearer x.y.z", 200)).isNull();
        assertThat(cacheHeaderFor("GET", "/api/v1/skus",
                new Cookie[]{ new Cookie("accessToken", "t") }, null, 200)).isNull();
    }

    @Test
    @DisplayName("GET 이 아니면 캐시하지 않는다")
    void onlyGet() throws Exception {
        // 헤더는 상태 코드가 정해지기 전(doFilter 앞)에 걸므로 코드로는 못 거른다.
        // 대신 GET 이 아닌 요청은 확실히 뺀다.
        assertThat(cacheHeaderFor("POST", "/api/v1/skus", null, null, 200)).isNull();
        assertThat(cacheHeaderFor("PUT", "/api/v1/skus", null, null, 200)).isNull();
        assertThat(cacheHeaderFor("DELETE", "/api/v1/skus/A1", null, null, 200)).isNull();
    }
}
