package com.koala.koalaback.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.time.Duration;

@Configuration
public class WebConfig {

    /**
     * 외부 API 호출용 RestTemplate — 현재 사용처는 결제(PG) 연동뿐이다.
     *
     * <p>타임아웃이 없으면 PG 가 응답하지 않을 때 요청 스레드가 무한정 붙잡히고,
     * 결제 승인 경로에서는 DB 커넥션까지 함께 묶인다. 반드시 상한을 둔다.
     *
     * <p>읽기 타임아웃이 걸렸다고 해서 승인이 실패한 것은 아니다.
     * 승인 여부는 {@code PaymentProvider.lookup()} 재조회로 확정한다.
     */
    @Bean
    public RestTemplate restTemplate(
            @Value("${payment.http.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${payment.http.read-timeout-ms:10000}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return new RestTemplate(factory);
    }

    /**
     * 로컬 개발 환경: 업로드된 파일을 /uploads/** 경로로 서빙
     */
    @Bean
    @Profile("local")
    public WebMvcConfigurer localUploadResourceHandler(
            @Value("${koala.storage.upload-dir:./uploads}") String uploadDir) {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                // 절대경로로 변환 (./uploads → /absolute/path/uploads/)
                String absolutePath = new File(uploadDir).getAbsolutePath() + "/";
                registry.addResourceHandler("/uploads/**")
                        .addResourceLocations("file:" + absolutePath);
            }
        };
    }
}

