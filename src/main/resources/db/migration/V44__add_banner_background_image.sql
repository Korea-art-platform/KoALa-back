-- ============================================================
-- V44: 히어로 배경 이미지
-- ============================================================
--
-- 조립 방식(COMPOSED) 히어로의 단색 배경 대신 깔 수 있는 배경 이미지. 선택 값이며
-- 비우면 지금처럼 배경색만 쓴다.
--
-- 재실행 가능하게 없을 때만 추가한다.

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE banners ADD COLUMN background_image_url VARCHAR(700) NULL COMMENT ''히어로 배경 이미지 — 비우면 배경색만''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'banners' AND COLUMN_NAME = 'background_image_url'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
