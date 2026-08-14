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

@DisplayName("어드민 엔드포인트 보안")
class AdminEndpointSecurityTest extends IntegrationTestSupport {
    private static final Set<String> PUBLIC_ADMIN_PATHS = Set.of("/admin/api/v1/auth/login");

    private static final String ADMIN_PREFIX = "/admin/api";

    @Autowired private WebApplicationContext context;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired private Filter springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

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

                String path = ep.path().replaceAll("\\{[^}]+\\}", "1");

                int status = mockMvc.perform(request(ep.method(), path))
                        .andReturn().getResponse().getStatus();

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

            assertThat(status)
                    .as("로그인 경로가 인증 필터에 막히면 안 된다")
                    .isNotEqualTo(403);
        }
    }
}
