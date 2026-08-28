package com.koala.koalaback.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
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
 * 화이트리스트로만 연다. 주문·장바구니·내 정보처럼 사람마다 다른 응답이 실수로
 * 캐시되면 남의 정보가 보일 수 있어, "열어도 되는 것"을 하나씩 지정한다.
 *
 * stale-while-revalidate 를 함께 준다. 캐시가 만료돼도 CloudFront 는 낡은 응답을
 * 즉시 돌려주면서 뒤에서 원본을 새로 받아 둔다 — 사용자는 만료 순간에도 기다리지
 * 않는다. 대신 수정이 반영되는 지연은 max-age 와 이 값을 합친 만큼이다.
 */
@Order(20)
@Component
public class PublicCacheHeaderFilter extends OncePerRequestFilter {

    private static final String CACHE_VALUE = "public, max-age=60, stale-while-revalidate=60";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(req, res);

        // 응답이 다 만들어진 뒤에 덮어쓴다. 그래야 Security 가 앞서 박아 둔
        // no-store 를 확실히 지운다.
        if (isCacheable(req, res)) {
            res.setHeader("Cache-Control", CACHE_VALUE);
        }
    }

    private boolean isCacheable(HttpServletRequest req, HttpServletResponse res) {
        if (!"GET".equals(req.getMethod())) return false;
        if (res.getStatus() != HttpServletResponse.SC_OK) return false;

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
