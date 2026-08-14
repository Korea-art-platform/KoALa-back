package com.koala.koalaback;

import com.koala.koalaback.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("애플리케이션 기동")
class ArsobackApplicationTests extends IntegrationTestSupport {
    @Test
    @DisplayName("스프링 컨텍스트가 로드된다")
    void contextLoads() {
    }
}
