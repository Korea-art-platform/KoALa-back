-- ============================================================
-- V40: 영어 화면용 영문 글 칸
-- ============================================================
--
-- 작가 소개·작가 노트·약력, 공지 제목·본문, 입점 매장 이름·주소·소개,
-- 상품 설명·소재·소재 설명·포장 제목·포장 설명에 영문 칸을 둔다.
-- 비어 있으면 영어 화면에서도 한글을 그대로 보여 준다.
-- 매장 영문 주소는 한글 주소처럼 암호화해 저장하므로 길이를 넉넉히 둔다.
--
-- 재실행 가능하게 칸마다 없을 때만 추가한다.

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE artists ADD COLUMN description_en LONGTEXT NULL COMMENT ''작가 소개 영문''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'artists' AND COLUMN_NAME = 'description_en'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE artists ADD COLUMN artist_note_en LONGTEXT NULL COMMENT ''작가 노트 영문''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'artists' AND COLUMN_NAME = 'artist_note_en'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE artist_careers ADD COLUMN content_en VARCHAR(1000) NULL COMMENT ''약력 영문''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'artist_careers' AND COLUMN_NAME = 'content_en'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE notices ADD COLUMN title_en VARCHAR(200) NULL COMMENT ''공지 제목 영문''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notices' AND COLUMN_NAME = 'title_en'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE notices ADD COLUMN content_en TEXT NULL COMMENT ''공지 본문 영문''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notices' AND COLUMN_NAME = 'content_en'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE partner_stores ADD COLUMN name_en VARCHAR(200) NULL COMMENT ''매장 이름 영문''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'partner_stores' AND COLUMN_NAME = 'name_en'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE partner_stores ADD COLUMN address_en VARCHAR(1024) NULL COMMENT ''매장 주소 영문 (암호화)''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'partner_stores' AND COLUMN_NAME = 'address_en'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE partner_stores ADD COLUMN description_en TEXT NULL COMMENT ''매장 소개 영문''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'partner_stores' AND COLUMN_NAME = 'description_en'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE skus ADD COLUMN description_en LONGTEXT NULL COMMENT ''상품 설명 영문''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skus' AND COLUMN_NAME = 'description_en'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE skus ADD COLUMN material_en VARCHAR(300) NULL COMMENT ''소재 영문''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skus' AND COLUMN_NAME = 'material_en'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE skus ADD COLUMN material_description_en LONGTEXT NULL COMMENT ''소재 설명 영문''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skus' AND COLUMN_NAME = 'material_description_en'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE skus ADD COLUMN packaging_title_en VARCHAR(200) NULL COMMENT ''포장 제목 영문''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skus' AND COLUMN_NAME = 'packaging_title_en'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE skus ADD COLUMN packaging_description_en LONGTEXT NULL COMMENT ''포장 설명 영문''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skus' AND COLUMN_NAME = 'packaging_description_en'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
