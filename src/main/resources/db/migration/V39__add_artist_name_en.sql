-- ============================================================
-- V39: 작가 영문 이름
-- ============================================================
--
-- 영어 화면에서 작가 이름을 영문으로 보여 준다. 비어 있으면 한글 이름을 그대로 쓴다.
--
-- 재실행 가능하게 없을 때만 추가한다.

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE artists ADD COLUMN name_en VARCHAR(150) NULL COMMENT ''작가 영문 이름 — 영어 화면용, 비면 한글 이름'' AFTER name',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'artists' AND COLUMN_NAME = 'name_en'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
