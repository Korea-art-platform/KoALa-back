-- ============================================================
-- V33: 메인 히어로 쇼케이스 — 작품 연결 + 구성 이미지 3장
-- ============================================================
--
-- 쇼핑하기·작가 둘러보기 주소와 가격은 연결한 작품에서 가져온다. 배경색은 기존 bg_color 를 쓴다.
-- FK 는 걸지 않는다. 운영 스키마는 파일과 다를 수 있고, 상품은 소프트 삭제라 행이 남는다.
-- 재실행 가능하게 없을 때만 추가한다.

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE banners ADD COLUMN sku_id BIGINT NULL COMMENT ''히어로 작품''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'banners' AND COLUMN_NAME = 'sku_id'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE banners ADD COLUMN effect_image_url1 VARCHAR(700) NULL COMMENT ''구성 이미지 1 — 오른쪽 위''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'banners' AND COLUMN_NAME = 'effect_image_url1'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE banners ADD COLUMN effect_image_url2 VARCHAR(700) NULL COMMENT ''구성 이미지 2 — 오른쪽 아래''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'banners' AND COLUMN_NAME = 'effect_image_url2'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE banners ADD COLUMN effect_image_url3 VARCHAR(700) NULL COMMENT ''구성 이미지 3 — 왼쪽 아래''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'banners' AND COLUMN_NAME = 'effect_image_url3'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
