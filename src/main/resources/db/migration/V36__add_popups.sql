-- ============================================================
-- V36: 팝업(프로모션 모달)
-- ============================================================
--
-- 어드민이 등록한 팝업을 고객 화면에 띄운다.
--   language     = 'ko' | 'en'. 고객 화면 언어별로 따로 등록한다.
--   display_type = 'IMAGE'    이미지 한 장을 띄운다. image_url 필수.
--                  'TEMPLATE' 정해진 틀에 title·body 를 채워 띄운다.
--   placement    = 'HOME' 홈에서만, 'ALL' 모든 화면에서.
--   show_dismiss = "오늘 하루 보지 않기" 노출 여부.
--   show_link_button = 바로가기 버튼 노출 여부. 켜면 landing_url 필수(서비스에서 검사).
--
-- 새로 만든 팝업은 비활성으로 시작한다 — 확인 전에 고객에게 뜨지 않게.
--
-- ── 재실행 가능 ──────────────────────────────────────────────
-- 운영은 baseline 이후 스키마가 파일과 어긋날 수 있어 IF NOT EXISTS 로 감싼다.
--
-- ── collation ────────────────────────────────────────────────
-- 이웃 테이블과 맞춰 utf8mb4_unicode_ci 로 통일한다.
--
-- ── 인덱스 ───────────────────────────────────────────────────
-- 공개 조회는 is_active = 1, language = ?, deleted_at IS NULL 로 거른 뒤
-- placement 로 한 번 더 거른다. 팝업은 몇 건 안 되므로 앞 세 컬럼이면 충분하다.

CREATE TABLE IF NOT EXISTS popups (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    popup_code          VARCHAR(32)   NOT NULL,
    title               VARCHAR(200)  NOT NULL,
    is_active           TINYINT(1)    NOT NULL DEFAULT 0,
    show_dismiss        TINYINT(1)    NOT NULL DEFAULT 1,
    language            VARCHAR(2)    NOT NULL,
    display_type        VARCHAR(20)   NOT NULL,
    image_url           VARCHAR(1024) NULL,
    body                VARCHAR(2000) NULL,
    show_link_button    TINYINT(1)    NOT NULL DEFAULT 0,
    placement           VARCHAR(20)   NOT NULL,
    landing_url         VARCHAR(1024) NULL,
    sort_order          INT           NOT NULL DEFAULT 0,
    created_by_admin_id BIGINT        NULL,
    updated_by_admin_id BIGINT        NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    deleted_at          DATETIME(6)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_popups_code (popup_code),
    KEY idx_popups_active_lang (is_active, language, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
