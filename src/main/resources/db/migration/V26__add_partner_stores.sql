-- ============================================================
-- V26: 입점 매장(파트너 스토어)
-- ============================================================
--
-- 공식 입점 매장 목록을 공개 페이지에 노출하고 어드민에서 관리한다.
--   name / description / map_url = 공개용(평문)
--   zip_code / address / address_detail / phone / phone2 / email
--     = 연락처·위치 PII → AES-GCM 컬럼 암호화(enc: 접두).
--       암호문이 base64 로 길어지므로 넉넉한 길이로 잡는다.
--
-- ── 재실행 가능 ──────────────────────────────────────────────
-- 운영은 baseline 이후 스키마가 파일과 어긋날 수 있어 IF NOT EXISTS 로 감싼다.
--
-- ── collation ────────────────────────────────────────────────
-- 이웃 테이블과 맞춰 utf8mb4_unicode_ci 로 통일한다.
-- (collation 불일치로 마이그레이션이 터진 전례가 있다.)

CREATE TABLE IF NOT EXISTS partner_stores (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    store_code      VARCHAR(40)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    zip_code        VARCHAR(200) NULL,
    address         VARCHAR(1024) NOT NULL,
    address_detail  VARCHAR(1024) NULL,
    phone           VARCHAR(512) NOT NULL,
    phone2          VARCHAR(512) NULL,
    email           VARCHAR(512) NULL,
    description     TEXT         NULL,
    map_url         VARCHAR(700) NULL,
    is_active       TINYINT(1)   NOT NULL DEFAULT 1,
    sort_order      INT          NOT NULL DEFAULT 0,
    created_by_admin_id BIGINT   NULL,
    created_at      DATETIME(6)  NULL,
    updated_at      DATETIME(6)  NULL,
    deleted_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_partner_stores_code (store_code),
    KEY idx_partner_stores_active (is_active, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
