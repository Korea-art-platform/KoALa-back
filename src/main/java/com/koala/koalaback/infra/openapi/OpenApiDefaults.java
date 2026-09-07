package com.koala.koalaback.infra.openapi;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API 문서의 기본값.
 *
 * 배포되는 jar 에는 application.yml 이 들어 있지 않다 — .gitignore 가 막고 있고
 * 워크플로도 만들지 않는다. 설정은 전부 서버의 application-prod.yml 에서 온다.
 * 그래서 서버 파일에 아무것도 없을 때 무엇이 되는지를 여기서 정해 둔다.
 *
 * 기본은 꺼짐이다. 설정을 빠뜨린 서버에서 문서가 열리는 쪽보다, 열려고 했는데
 * 안 열리는 쪽이 낫다. 실결제가 도는 서버다.
 *
 * 경로도 함께 못 박는다. springdoc 기본값 /v3/api-docs 는 SecurityConfig 의
 * 매처 /api-docs/** 에 걸리지 않아 anyRequest().authenticated() 로 떨어진다 —
 * 로그인한 사람 누구나 전체 명세를 읽게 된다. /api-docs 로 두면 매처 안에 들어와
 * 운영에서는 ADMIN 만 닿는다.
 *
 * addLast 로 넣어 가장 낮은 우선순위에 둔다. 서버 application-prod.yml 이나
 * 로컬 application.yml 이 같은 키를 적으면 그쪽이 이긴다.
 */
public class OpenApiDefaults implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "koalaOpenApiDefaults";

    public OpenApiDefaults(ConfigurableBootstrapContext bootstrapContext) {
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(SOURCE_NAME)) return;

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("springdoc.api-docs.enabled", "false");
        defaults.put("springdoc.swagger-ui.enabled", "false");
        defaults.put("springdoc.api-docs.path", "/api-docs");

        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, defaults));
    }
}
