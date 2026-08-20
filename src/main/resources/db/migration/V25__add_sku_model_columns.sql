-- ============================================================
-- V25: 상품에 모델 / 세부모델명 추가
-- ============================================================
--
-- 지금은 상품에 이름(name) 하나뿐이라, 같은 작품의 색상·버전 변형을 구분할 자리가 없다.
--   model          = 작품명 (예: '닥스훈트')
--   sub_model_name = 변형   (예: '청색')
--
-- 둘 다 nullable 로 넣는다. 기존 7개 상품에는 값이 없고, 어드민에서 채운다.
-- NOT NULL + 기본값으로 넣으면 기존 상품의 model 이 빈 문자열로 굳어 오히려 지저분해진다.
--
-- ── 재실행 가능해야 한다 ──────────────────────────────────────
-- 운영은 V14 에서 baseline 되어 파일과 실제 스키마가 일치하지 않는다. "없을 때만" 으로 감싼다.
--
-- ── COLLATE 를 명시한다 ───────────────────────────────────────
-- 이 DB 는 테이블마다 collation 이 다르다. skus 는 utf8mb4_unicode_ci 이므로 맞춘다.
-- (V21 의 collation 불일치로 V22 가 터져 서비스가 내려간 전례가 있다.)

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE skus ADD COLUMN model VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''작품명(모델)''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skus' AND COLUMN_NAME = 'model'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE skus ADD COLUMN sub_model_name VARCHAR(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT ''변형명(색상 등)''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skus' AND COLUMN_NAME = 'sub_model_name'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
