package com.koala.koalaback.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * API 문서 구성.
 *
 * 이미 돌고 있는 API 를 적기만 한다. 여기에 오는 변경이 동작을 바꾸는 일은 없어야 한다.
 *
 * 운영에서는 application-prod.yml 이 springdoc 을 끄지만 프로파일로 한 번 더 막는다.
 * 설정 한 줄이 빠지는 것과 빈이 아예 안 뜨는 것은 다르다.
 *
 * 공개와 어드민을 갈라 둔다. 161개를 한 목록에 담으면 공개 69개를 볼 때마다
 * 어드민 92개를 지나가야 한다.
 *
 * 경로가 아니라 패키지로 가른다. PaymentController·ReviewController·SkuController·
 * UserController 는 클래스에 베이스 경로가 없고 메서드마다 전체 경로를 적어,
 * 경로 패턴으로는 잡히지 않는다.
 */
@Configuration
@Profile("!prod")
public class OpenApiConfig {

    private static final String API_BASE = "com.koala.koalaback.api";

    @Bean
    public OpenAPI koalaOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("KOALA API")
                .version("v1")
                .description("한국 작가 미술품 거래 플랫폼."));
    }

    /**
     * 공개 API.
     *
     * api 패키지 전체를 훑고 어드민 경로만 뺀다. 하위 패키지를 하나씩 적으면
     * 도메인이 늘 때마다 여기에 줄을 더해야 하고, 빠뜨리면 그 도메인이 문서에서
     * 조용히 사라진다.
     */
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("1-공개")
                .packagesToScan(API_BASE)
                .pathsToExclude("/admin/**")
                .build();
    }

    /**
     * 어드민 API.
     *
     * 패키지가 아니라 경로로 가른다. PaymentController 는 api.payment 에 있으면서
     * /admin/api/v1/payments/{paymentNo}/cancel · /resolve 두 개를 갖는다.
     * admin 패키지만 훑으면 이 둘이 공개 그룹의 /admin 제외에도 걸려 어느 쪽에도
     * 안 남는다 — 문서에서 조용히 사라진다.
     *
     * 공개 그룹의 제외 조건과 정확히 반대라, 둘을 합치면 api 패키지가 빠짐없이 갈린다.
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("2-어드민")
                .packagesToScan(API_BASE)
                .pathsToMatch("/admin/**")
                .build();
    }
}
