-- ============================================================
-- V29: 상품 등록 항목 정리 — 색상 · 영문명 · 무게 단위
-- ============================================================
--
-- 등록 화면이 상품명 · 슬러그 · 모델 · 세부모델명을 각각 물어 중복되고
-- 헷갈렸다. 앞으로는 세 가지만 받는다.
--
--   모델        예) 닥쿤이, 순정남
--   세부모델명  예) 호돌이, 호순이
--   색상        예) 검정, 파랑
--
-- 상품명(name)과 슬러그(slug)는 이 셋을 조합해 서버가 만든다. 두 컬럼은
-- 주문·검색·URL 이 이미 쓰고 있어 없앨 수 없고, 사람이 직접 채울 이유도 없다.
--
-- 각 항목을 한국어와 영어로 나란히 받는다. 해외 노출과 슬러그 생성 때문이다.
-- 한글 슬러그는 URL 인코딩되면 읽을 수 없어진다.
--
-- ── 무게 kg → g ──────────────────────────────────────────────
-- 아트토이는 대부분 1kg 미만이라 0.35 처럼 소수를 넣어야 했다. g 로 받는다.
-- 기존 weight_kg 는 지우지 않고 값만 옮긴다. 되돌릴 여지를 남긴다.
--
-- ── NOT NULL 을 걸지 않는 이유 ────────────────────────────────
-- 이미 등록된 상품에는 색상과 영문명이 없다. NOT NULL 로 만들면 기존 행이
-- 걸려 마이그레이션이 실패한다. 필수 여부는 등록 화면과 서버 검증에서 막고,
-- 컬럼은 NULL 을 허용한다.
--
-- ── 재실행 가능해야 한다 ──────────────────────────────────────
-- 운영은 V14 에서 baseline 되어 파일과 실제 스키마가 다르다.
-- information_schema 로 확인하고 없을 때만 실행한다.

-- ── 1. 영문 모델 · 세부모델명 · 색상 ─────────────────────────
SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE skus ADD COLUMN model_en VARCHAR(150) NULL COMMENT ''모델 영문명''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skus' AND COLUMN_NAME = 'model_en'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE skus ADD COLUMN sub_model_name_en VARCHAR(150) NULL COMMENT ''세부모델명 영문''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skus' AND COLUMN_NAME = 'sub_model_name_en'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE skus ADD COLUMN color VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''색상''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skus' AND COLUMN_NAME = 'color'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE skus ADD COLUMN color_en VARCHAR(100) NULL COMMENT ''색상 영문''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skus' AND COLUMN_NAME = 'color_en'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ── 2. 무게 g ────────────────────────────────────────────────
SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE skus ADD COLUMN weight_g INT NULL COMMENT ''무게(g)''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skus' AND COLUMN_NAME = 'weight_g'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 기존 kg 값을 g 로 옮긴다. 이미 채워진 행은 건드리지 않는다.
UPDATE skus
   SET weight_g = ROUND(weight_kg * 1000)
 WHERE weight_g IS NULL
   AND weight_kg IS NOT NULL;

-- ── 3. 카테고리 코드 자동 생성 대비 ──────────────────────────
-- 앞으로 관리자는 표시 이름만 입력하고 코드는 서버가 만든다.
-- code 는 VARCHAR(50) UNIQUE 라 그대로 쓸 수 있어 스키마 변경이 없다.
