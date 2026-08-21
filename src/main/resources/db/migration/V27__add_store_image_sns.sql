-- ============================================================
-- V27: 입점 매장에 사진 / SNS 링크 추가
-- ============================================================
--
-- image_url = 매장 사진(공개, 평문)
-- sns_url   = 인스타 등 SNS 링크(공개, 평문)
-- 둘 다 공개 정보라 암호화하지 않는다.
--
-- 재실행 가능해야 하므로 "없을 때만" 으로 감싼다.

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE partner_stores ADD COLUMN sns_url VARCHAR(700) NULL',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'partner_stores' AND COLUMN_NAME = 'sns_url'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE partner_stores ADD COLUMN image_url VARCHAR(700) NULL',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'partner_stores' AND COLUMN_NAME = 'image_url'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
