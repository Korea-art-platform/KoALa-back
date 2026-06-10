package com.koala.koalaback.global.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Flyway 수동 설정.
 *
 * <p>Spring Boot 4.x 의 {@code FlywayAutoConfiguration} 은 JPA(entityManagerFactory)와
 * 양방향 depends-on 관계를 만들어 부팅 시
 * "Circular depends-on relationship between 'flyway' and 'entityManagerFactory'" 로 실패한다.
 *
 * <p>따라서 자동설정에 의존하지 않고 Flyway 빈을 직접 등록하여 부팅 시 {@code migrate()} 를 실행한다.
 * 이 빈은 {@link DataSource} 에만 의존하므로 entityManagerFactory 와 순환이 발생하지 않는다.
 *
 * <p>단, 자동설정을 끄면서 "Flyway 마이그레이션이 Hibernate 스키마 validate 보다 먼저 실행되도록"
 * 하는 순서 보장도 사라진다. 이를 복원하기 위해 entityManagerFactory 가 flyway 빈에 의존하도록
 * BeanFactoryPostProcessor 로 depends-on 을 주입한다. (단방향이라 순환 없음)
 */
@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    public Flyway flyway(
            DataSource dataSource,
            @Value("${spring.flyway.locations:classpath:db/migration}") String[] locations,
            @Value("${spring.flyway.baseline-on-migrate:true}") boolean baselineOnMigrate,
            @Value("${spring.flyway.baseline-version:14}") String baselineVersion,
            @Value("${spring.flyway.validate-on-migrate:false}") boolean validateOnMigrate
    ) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(baselineVersion)
                .validateOnMigrate(validateOnMigrate)
                .load();
    }

    /**
     * entityManagerFactory 가 flyway 빈(= migrate() 완료) 이후에 생성되도록 강제한다.
     * 없으면 Hibernate validate 가 Flyway 마이그레이션보다 먼저 실행되어,
     * 새 컬럼 추가 마이그레이션에서 "missing column" 으로 부팅이 실패한다.
     */
    @Bean
    public static BeanFactoryPostProcessor entityManagerFactoryDependsOnFlyway() {
        return (ConfigurableListableBeanFactory beanFactory) -> {
            if (beanFactory.containsBeanDefinition("entityManagerFactory")) {
                BeanDefinition bd = beanFactory.getBeanDefinition("entityManagerFactory");
                List<String> dependsOn = new ArrayList<>();
                if (bd.getDependsOn() != null) {
                    Collections.addAll(dependsOn, bd.getDependsOn());
                }
                if (!dependsOn.contains("flyway")) {
                    dependsOn.add("flyway");
                }
                bd.setDependsOn(dependsOn.toArray(new String[0]));
            }
        };
    }
}
