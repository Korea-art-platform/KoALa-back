-- ============================================================
-- V28: 영상 전용 배너 허용 + 자주 타는 조회에 인덱스
-- ============================================================
--
-- ── 1. banners.image_url 을 NULL 허용으로 ─────────────────────
-- V24 에서 video_url 을 추가하며 image_url 은 포스터로 쓰기로 했다.
-- 그런데 image_url 이 NOT NULL 로 남아 있어, 포스터 없이 영상만 넣은
-- 배너는 INSERT 자체가 거부된다. 어드민에서 미디어 유형을 영상으로
-- 고르면 바로 걸리는 자리다.
--
-- ── 2. payments.pg_transaction_id 인덱스 ──────────────────────
-- 결제 웹훅이 들어올 때마다 findByPgTransactionId 로 결제 건을 찾는다.
-- 인덱스가 없어 결제 건수가 늘수록 매번 풀스캔이 된다. 승인 통보는
-- 지연되면 주문이 결제완료로 안 바뀌므로 특히 민감하다.
-- UNIQUE 는 쓰지 않는다 — PG 가 같은 tid 로 재통보하는 경우가 있고,
-- 그때 제약 위반으로 통보 처리 전체가 실패하면 더 나쁘다.
--
-- ── 3. banners(banner_type, is_active, sort_order) 인덱스 ─────
-- 홈을 열 때마다 MAIN 과 MAIN_SUB 를 각각 조회한다. 정렬까지 인덱스로
-- 해결되도록 sort_order 를 뒤에 붙였다.
--
-- ── 재실행 가능해야 한다 ──────────────────────────────────────
-- 운영은 V14 에서 baseline 되어 파일과 실제 스키마가 다를 수 있다.
-- MySQL 8 에는 ADD INDEX IF NOT EXISTS 가 없으므로 information_schema 를
-- 보고 없을 때만 실행한다.

-- 1. image_url NULL 허용
SET @ddl := (
    SELECT IF(COUNT(*) = 1,
        'ALTER TABLE banners MODIFY COLUMN image_url VARCHAR(700) NULL COMMENT ''배너 이미지 — 영상 배너에서는 포스터로 쓰이며 비어 있을 수 있다''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'banners'
      AND COLUMN_NAME = 'image_url'
      AND IS_NULLABLE = 'NO'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 2. payments.pg_transaction_id
SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'CREATE INDEX idx_payments_pg_transaction_id ON payments (pg_transaction_id)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'payments'
      AND INDEX_NAME = 'idx_payments_pg_transaction_id'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 3. banners(banner_type, is_active, sort_order)
SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'CREATE INDEX idx_banners_type_active_sort ON banners (banner_type, is_active, sort_order)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'banners'
      AND INDEX_NAME = 'idx_banners_type_active_sort'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
