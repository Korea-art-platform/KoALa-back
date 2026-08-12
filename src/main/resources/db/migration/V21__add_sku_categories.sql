-- ============================================================
-- V21: 상품 카테고리 (대분류 · 소분류) — 관리자가 직접 추가
-- ============================================================
--
-- 지금까지 분류는 코드에 박혀 있었다.
--   sku_type : ARTWORK / GOODS  (DB CHECK 로 고정)
--   genre    : SkuGenre enum 8개 (코드로 고정)
-- 카테고리를 늘리려면 배포가 필요했다. 운영에서 계속 추가해야 하므로 테이블로 옮긴다.
--
-- 구조는 계층이 아니라 '독립 2축' 이다.
--   대분류(MAIN) = 판매 형태  : 한정판 / 일반
--   소분류(SUB)  = 장르       : 조각 / 아트토이 / …
-- 계층으로 두면 '조각'을 한정판 밑과 일반 밑에 두 번 만들어야 하고,
-- 홈의 한정판 섹션과 소분류 섹션이 완전히 겹친다.

CREATE TABLE sku_categories (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    type        VARCHAR(10)  NOT NULL COMMENT 'MAIN(대분류) / SUB(소분류)',
    code        VARCHAR(50)  NOT NULL COMMENT '상품에 저장되는 값',
    name        VARCHAR(50)  NOT NULL COMMENT '화면 표시명',
    sort_order  INT          NOT NULL DEFAULT 0,
    is_active   TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_categories_type_code (type, code),
    KEY idx_sku_categories_type_active (type, is_active, sort_order),
    CONSTRAINT ck_sku_categories_type CHECK (type IN ('MAIN', 'SUB'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── 초기 데이터 ──────────────────────────────────────────────
-- 대분류: 화면에 쓰이던 한정판/일반
INSERT INTO sku_categories (type, code, name, sort_order) VALUES
    ('MAIN', 'LIMITED', '한정판', 1),
    ('MAIN', 'NORMAL',  '일반',   2);

-- 소분류: 기존 SkuGenre enum 을 그대로 옮긴다.
-- 운영에는 SCULPTURE, ART_TOY 만 쓰이고 있으나 나머지도 함께 넣어 선택지를 유지한다.
INSERT INTO sku_categories (type, code, name, sort_order) VALUES
    ('SUB', 'SCULPTURE',    '조각',          1),
    ('SUB', 'ART_TOY',      '아트 토이',      2),
    ('SUB', 'PAINTING',     '페인팅',        3),
    ('SUB', 'PRINT',        '판화 / 프린트',  4),
    ('SUB', 'PHOTOGRAPH',   '사진',          5),
    ('SUB', 'INSTALLATION', '설치 미술',      6),
    ('SUB', 'TEXTILE',      '섬유 / 직물',    7),
    ('SUB', 'OTHER',        '기타',          8);

-- ── 상품에 대분류 컬럼 추가 ──────────────────────────────────
-- genre 는 그대로 소분류 코드를 담는다(컬럼명만 옛 이름).
-- sku_type(ARTWORK/GOODS)은 더 이상 화면에서 쓰지 않지만, 기존 데이터와
-- NOT NULL 제약 때문에 컬럼은 남겨둔다.
ALTER TABLE skus
    ADD COLUMN main_category VARCHAR(50) NOT NULL DEFAULT 'NORMAL'
    COMMENT '대분류 코드 — sku_categories.code (type=MAIN)';

-- 기존 데이터 이관: 한정판 여부로 대분류를 채운다
UPDATE skus SET main_category = 'LIMITED' WHERE is_limited_edition = 1;

CREATE INDEX idx_skus_main_category ON skus (main_category);

-- ── 에디션 제약 완화 ─────────────────────────────────────────
-- 기존: 한정판이면 editionSize·Number 가 '필수'
-- 변경: 둘 다 선택 입력. 총 수량만 정해두고 번호는 나중에 채우는 경우가 있다.
--       단 번호만 있고 총 수량이 없으면 의미가 없으므로 그건 계속 막는다.
--
-- 대분류가 동적으로 늘어나므로 특정 코드('LIMITED')를 CHECK 에 박지 않는다.
-- "일반은 에디션을 갖지 않는다" 같은 업무 규칙은 애플리케이션에서 검증한다.
ALTER TABLE skus DROP CHECK ck_skus_edition;

ALTER TABLE skus ADD CONSTRAINT ck_skus_edition CHECK (
    (edition_size IS NULL OR edition_size > 0)
    AND (edition_number IS NULL
         OR (edition_size IS NOT NULL AND edition_number > 0
             AND edition_number <= edition_size))
);
