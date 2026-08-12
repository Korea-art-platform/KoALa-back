-- ============================================================
-- V7: 작가 대표 작품(featured_sku_id) 컬럼 추가
--     이미 수동으로 추가된 환경에서도 충돌 없이 실행됩니다.
-- ============================================================

-- 1. artists.featured_sku_id 컬럼 (없는 경우에만 추가)
SET @exist_col = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'artists'
      AND COLUMN_NAME  = 'featured_sku_id'
);
SET @sql_col = IF(
    @exist_col = 0,
    'ALTER TABLE artists ADD COLUMN featured_sku_id BIGINT DEFAULT NULL AFTER is_active',
    'SELECT 1'
);
PREPARE stmt FROM @sql_col;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. FK 제약조건 (없는 경우에만 추가)
SET @exist_fk = (
    SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA   = DATABASE()
      AND CONSTRAINT_NAME     = 'fk_artists_featured_sku'
);
SET @sql_fk = IF(
    @exist_fk = 0,
    'ALTER TABLE artists ADD CONSTRAINT fk_artists_featured_sku FOREIGN KEY (featured_sku_id) REFERENCES skus (id) ON DELETE SET NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql_fk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
