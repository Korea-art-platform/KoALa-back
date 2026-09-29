-- ============================================================
-- V37: 히어로 뒤 작품명 이미지
-- ============================================================
--
-- 지금은 작품 이름을 웹 글꼴로 그려 넣는다. 글꼴을 따로 만들고 누끼를 딴 타이포를
-- 쓰고 싶다는 요청이 있어, 배너마다 그 이미지를 올릴 수 있게 한다.
-- 비어 있으면 지금처럼 글자로 그린다.
--
-- 재실행 가능하게 없을 때만 추가한다.

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE banners ADD COLUMN title_image_url VARCHAR(700) NULL COMMENT ''히어로 뒤 작품명 이미지 — 투명 PNG''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'banners' AND COLUMN_NAME = 'title_image_url'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
