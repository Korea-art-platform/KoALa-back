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
    @Bean
    public RestTemplate restTemplate(
            @Value("${payment.http.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${payment.http.read-timeout-ms:10000}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return new RestTemplate(factory);
    }

    @Bean
    @Profile("local")
    public WebMvcConfigurer localUploadResourceHandler(
            @Value("${koala.storage.upload-dir:./uploads}") String uploadDir) {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                String absolutePath = new File(uploadDir).getAbsolutePath() + "/";
                registry.addResourceHandler("/uploads/**")
                        .addResourceLocations("file:" + absolutePath);
            }
        };
    }
}
