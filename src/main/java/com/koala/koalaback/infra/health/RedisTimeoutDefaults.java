package com.koala.koalaback.infra.health;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

public class RedisTimeoutDefaults implements EnvironmentPostProcessor {
    static final String COMMAND_TIMEOUT = "300ms";
    static final String CONNECT_TIMEOUT = "300ms";

    private static final String SOURCE_NAME = "koalaRedisTimeoutDefaults";

    public RedisTimeoutDefaults(ConfigurableBootstrapContext bootstrapContext) {
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(SOURCE_NAME)) return;

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("spring.data.redis.timeout", COMMAND_TIMEOUT);
        defaults.put("spring.data.redis.connect-timeout", CONNECT_TIMEOUT);

        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, defaults));
    }
}
