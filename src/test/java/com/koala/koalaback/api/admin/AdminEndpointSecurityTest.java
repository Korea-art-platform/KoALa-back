package com.koala.koalaback.api.admin;

import com.koala.koalaback.support.IntegrationTestSupport;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;


/**
 * 어드민 엔드포인트가 빠짐없이 보호되는지 검증한다.
 *
 * <h3>왜 필요한가</h3>
 * <p>{@code @PreAuthorize} 를 한 곳 빠뜨리면 상품·주문·회원 정보가 그대로 열린다.
 * 그런데 빠뜨렸다는 사실은 화면에 아무 표시도 나지 않아, 누가 찾아내기 전까지 모른다.
 *
 * <p>엔드포인트를 여기에 손으로 나열하면 새 컨트롤러가 생길 때마다 같이 적어야 하고,
 * 잊으면 테스트는 통과하는데 구멍은 열린 채로 남는다. 그래서 <b>스프링이 실제로 등록한
 * 핸들러 목록을 훑어</b> 검사한다. 새 어드민 API 를 추가하면 자동으로 검사 대상이 된다.
 */
@DisplayName("어드민 엔드포인트 보안")
class AdminEndpointSecurityTest extends IntegrationTestSupport {

    /** 인증 없이 열려 있어야 하는 것 — 로그인은 토큰을 받기 전이라 당연히 통과해야 한다 */
    private static final Set<String> PUBLIC_ADMIN_PATHS = Set.of("/admin/api/v1/auth/login");

    private static final String ADMIN_PREFIX = "/admin/api";

    @Autowired private WebApplicationContext context;

    // 같은 타입의 빈이 여러 개라(actuator 등) 이름으로 콕 집어야 한다.
    // 우리가 만든 컨트롤러가 등록되는 것은 이 기본 매핑이다.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    /** 스프링 시큐리티 필터 체인 — 이게 빠지면 권한 검사 없이 컨트롤러가 바로 호출된다 */
    @Autowired private Filter springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    /** (HTTP 메서드, 경로, 핸들러 이름) 한 건 */
    private record Endpoint(HttpMethod method, String path, String handler) {
        @Override public String toString() { return method + " " + path + "  (" + handler + ")"; }
    }

    private List<Endpoint> adminEndpoints() {
        List<Endpoint> endpoints = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry
                : handlerMapping.getHandlerMethods().entrySet()) {

            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();

            Set<String> patterns = info.getPathPatternsCondition() != null
                    ? info.getPathPatternsCondition().getPatternValues()
                    : Set.of();

            for (String pattern : patterns) {
                if (!pattern.startsWith(ADMIN_PREFIX)) continue;

                Set<org.springframework.web.bind.annotation.RequestMethod> methods =
                        info.getMethodsCondition().getMethods();
                // 메서드를 지정하지 않은 매핑은 전부 받으므로 GET 으로 대표해 확인한다
                if (methods.isEmpty()) {
                    endpoints.add(new Endpoint(HttpMethod.GET, pattern, handler.getMethod().getName()));
                } else {
                    methods.forEach(m -> endpoints.add(
                            new Endpoint(HttpMethod.valueOf(m.name()), pattern, handler.getMethod().getName())));
                }
            }
        }
        return endpoints;
    }

    @Nested
    @DisplayName("선언")
    class Declaration {

        @Test
        @DisplayName("모든 어드민 핸들러에 @PreAuthorize 가 붙어 있다")
        void everyHandlerIsAnnotated() {
            List<String> unguarded = new ArrayList<>();

            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry
                    : handlerMapping.getHandlerMethods().entrySet()) {

                Set<String> patterns = entry.getKey().getPathPatternsCondition() != null
                        ? entry.getKey().getPathPatternsCondition().getPatternValues()
                        : Set.of();

                boolean isAdmin = patterns.stream().anyMatch(p -> p.startsWith(ADMIN_PREFIX));
                boolean isPublic = patterns.stream().allMatch(PUBLIC_ADMIN_PATHS::contains);
                if (!isAdmin || isPublic) continue;

                HandlerMethod handler = entry.getValue();
                boolean guarded =
                        AnnotatedElementUtils.hasAnnotation(handler.getMethod(), PreAuthorize.class)
                                || AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), PreAuthorize.class);

                if (!guarded) {
                    unguarded.add(handler.getBeanType().getSimpleName()
                            + "." + handler.getMethod().getName() + " → " + patterns);
                }
            }

            assertThat(unguarded)
                    .as("@PreAuthorize 가 없는 어드민 엔드포인트 — 인증 없이 열립니다")
                    .isEmpty();
        }

        @Test
        @DisplayName("검사 대상이 실제로 잡힌다 — 매핑 조회가 비면 이 테스트 전체가 무의미하다")
        void endpointsAreDiscovered() {
            assertThat(adminEndpoints())
                    .as("어드민 엔드포인트가 하나도 안 잡혔다면 경로 접두사나 매핑 조회가 잘못된 것")
                    .hasSizeGreaterThan(50);
        }
    }

    @Nested
    @DisplayName("실제 요청")
    class Runtime {

        @Test
        @DisplayName("비로그인 요청은 어느 어드민 엔드포인트에서도 성공하지 못한다")
        void anonymousIsRejectedEverywhere() throws Exception {
            List<String> leaked = new ArrayList<>();

            for (Endpoint ep : adminEndpoints()) {
                if (PUBLIC_ADMIN_PATHS.contains(ep.path())) continue;

                // {id} 같은 자리는 아무 값으로 채운다. 권한 검사는 본문·경로값보다 먼저 일어난다
                String path = ep.path().replaceAll("\\{[^}]+\\}", "1");

                int status = mockMvc.perform(request(ep.method(), path))
                        .andReturn().getResponse().getStatus();

                // 401/403 이 정상. 인증을 통과해 버리면(2xx) 정보가 새고,
                // 404/405 면 보안 이전에 매핑이 어긋난 것이다.
                if (status != 401 && status != 403) {
                    leaked.add(ep + " → " + status);
                }
            }

            assertThat(leaked)
                    .as("비로그인인데 401/403 이 아닌 엔드포인트")
                    .isEmpty();
        }

        @Test
        @DisplayName("어드민 로그인은 인증 없이 닿는다 — 막히면 아무도 로그인할 수 없다")
        void loginStaysReachable() throws Exception {
            int status = mockMvc.perform(request(HttpMethod.POST, "/admin/api/v1/auth/login")
                            .contentType("application/json")
                            .content("{\"loginId\":\"nobody\",\"password\":\"wrong\"}"))
                    .andReturn().getResponse().getStatus();

            // 자격증명이 틀려 거절되는 것(401)은 정상이다. 403 은 필터가 요청 자체를 막았다는 뜻이라
            // 아무도 어드민에 로그인할 수 없게 된다.
            assertThat(status)
                    .as("로그인 경로가 인증 필터에 막히면 안 된다")
                    .isNotEqualTo(403);
        }
    }
}
