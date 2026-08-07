-- 동시성 테스트 전용 no-op 마이그레이션.
--
-- FlywayConfig 는 Flyway 빈을 직접 등록하므로 spring.flyway.enabled=false 로는 끌 수 없다.
-- 테스트에서는 운영 마이그레이션 대신 이 빈 위치를 가리키고, 스키마는 Hibernate 가 생성한다.
-- (운영 마이그레이션 체인은 V13/V14 의 FK 타입 문제로 빈 DB 에서 재생되지 않는다)
SELECT 1;
