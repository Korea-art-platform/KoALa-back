-- V35: 주문자 찾기용 해시를 필수로 조인다
--
-- V34 배포 때 애플리케이션이 기존 주문의 해시를 모두 채웠다. 이제 해시가 빠진
-- 주문은 가입 연결·어드민 검색에서 영영 찾을 수 없으므로 DB 에서 막는다.
--
-- 조건부로 건다.
--   - 주문이 있고 해시가 전부 채워진 DB(운영)에서만 NOT NULL 을 건다.
--   - 빈 DB(새로 만든 로컬·테스트)나, 암호화 키 없이 쌓인 주문이 있는 로컬 DB 는 건너뛴다.
--     키가 없으면 해시를 만들 수 없어 제약을 걸면 로컬에서 주문이 막힌다.
--
-- 뒷자리 4자리는 짧은 번호에서 비어 있을 수 있어 조이지 않는다.

SET @total_rows = (SELECT COUNT(*) FROM orders);
SET @null_rows  = (SELECT COUNT(*) FROM orders
                   WHERE orderer_email_hash IS NULL OR orderer_phone_hash IS NULL);

SET @ddl = IF(@total_rows > 0 AND @null_rows = 0,
    'ALTER TABLE orders MODIFY COLUMN orderer_email_hash VARCHAR(64) NOT NULL, MODIFY COLUMN orderer_phone_hash VARCHAR(64) NOT NULL',
    'DO 0');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
