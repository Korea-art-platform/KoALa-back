package com.koala.koalaback.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 공개 GET API 에 짧은 캐시 헤더를 붙인다.
 *
 * Spring Security 는 기본으로 모든 응답에 "no-store" 를 박는다. 로그인 화면이
 * 캐시에 남는 것을 막으려는 안전장치지만, 그 탓에 배너·상품처럼 아무나 봐도
 * 되는 응답까지 CloudFront 가 캐시하지 못하고 매번 EC2 까지 왔다. 그 no-store
 * 를 끈 다음(SecurityConfig 의 cacheControl().disable()), 정말로 공개해도 되는
 * 경로에만 여기서 캐시를 허용한다.
 *
 * 헤더는 doFilter "전에" 건다. 응답이 한 번 커밋되면(본문이 나가기 시작하면)
 * 헤더를 더는 못 바꾼다. 컨트롤러가 실행되기 전에 미리 걸어 두어야, 응답이
 * 나갈 때 그 헤더가 함께 실린다.
 *
 * 그래서 상태 코드로는 거르지 못한다 — 아직 정해지기 전이다. 대신 경로와
 * 인증 여부로만 판단한다. 404·500 에도 캐시 헤더가 붙을 수 있지만, 짧은
 * max-age 라 문제되지 않고 CloudFront 는 4xx/5xx 를 짧게만 캐시한다.
 *
 * @Component 를 붙이지 않는다. 붙이면 Spring Boot 가 서블릿 필터로도 자동
 * 등록해, Security 체인에 넣은 것과 두 번 돈다. SecurityConfig 에서만 넣는다.
 */
public class PublicCacheHeaderFilter extends OncePerRequestFilter {

    private static final String CACHE_VALUE = "public, max-age=60, stale-while-revalidate=60";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (isCacheable(req)) {
            res.setHeader("Cache-Control", CACHE_VALUE);
        }
        chain.doFilter(req, res);
    }

    private boolean isCacheable(HttpServletRequest req) {
        if (!"GET".equals(req.getMethod())) return false;

        // 인증 쿠키·헤더가 실려 온 요청은 사람마다 응답이 갈릴 수 있으니 캐시하지 않는다.
        // 공개 목록도 로그인한 채로 열 수 있는데, 그 응답이 캐시에 박혀 비로그인에게
        // 나가면 곤란하다. 안전하게 인증이 붙은 요청은 통째로 뺀다.
        if (req.getHeader("Authorization") != null) return false;
        if (req.getCookies() != null) {
            for (var c : req.getCookies()) {
                String n = c.getName();
                if (n.equals("accessToken") || n.equals("refreshToken") || n.startsWith("JSESSION")) {
                    return false;
                }
            }
        }

        return isPublicPath(req.getRequestURI());
    }

    /**
     * 로그인 상태와 무관하게 늘 같은 응답인 경로만 연다.
     * 작가 상세(/api/v1/artists/{code})는 팔로우 여부가 섞여 제외한다.
     */
    private boolean isPublicPath(String uri) {
        if (uri.equals("/api/v1/banners")) return true;
        if (uri.equals("/api/v1/artists")) return true;
        if (uri.equals("/api/v1/skus")) return true;
        if (uri.equals("/api/v1/skus/genre-counts")) return true;
        if (uri.equals("/api/v1/skus/main-category-counts")) return true;
        // 상품 상세와 작가별 작품 목록. 리뷰·360프레임 같은 하위 경로는 제외한다.
        if (uri.startsWith("/api/v1/skus/") && uri.indexOf('/', 14) < 0) return true;
        if (uri.startsWith("/api/v1/artists/") && uri.endsWith("/skus")) return true;
        return false;
    }
}
