package com.koala.koalaback.infra.health;

import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

public class HealthGroupDefaults implements EnvironmentPostProcessor {
    static final String LIVENESS = "livenessState";
    static final String READINESS = "readinessState, db, redis, diskSpace";

    private static final String SOURCE_NAME = "koalaHealthGroupDefaults";

    public HealthGroupDefaults(ConfigurableBootstrapContext bootstrapContext) {
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(SOURCE_NAME)) return;

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("management.endpoint.health.group.liveness.include", LIVENESS);
        defaults.put("management.endpoint.health.group.readiness.include", READINESS);

        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, defaults));
    }
}
