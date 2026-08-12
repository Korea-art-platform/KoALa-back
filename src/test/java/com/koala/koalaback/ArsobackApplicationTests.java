package com.koala.koalaback;

import com.koala.koalaback.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 애플리케이션이 뜨는지만 보는 스모크 테스트.
 *
 * <p>예전에는 개발자 로컬 MySQL 에 그대로 붙었다. 그래서 마이그레이션 파일을 고치면
 * 로컬에 적용된 체크섬과 어긋나 이 테스트만 깨졌다 — 운영과는 아무 상관이 없는데도
 * 빌드가 빨갛게 되니, 진짜 문제인지 판단하는 데 시간이 든다.
 *
 * <p>다른 통합 테스트와 같은 컨테이너를 쓰면 누구 PC 에서 돌리든 결과가 같다.
 */
@DisplayName("애플리케이션 기동")
class ArsobackApplicationTests extends IntegrationTestSupport {

    @Test
    @DisplayName("스프링 컨텍스트가 로드된다")
    void contextLoads() {
    }
}
