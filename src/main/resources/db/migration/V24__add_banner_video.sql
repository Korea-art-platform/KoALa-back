-- ============================================================
-- V24: 배너 영상
-- ============================================================
--
-- 히어로 배너를 영상으로도 쓸 수 있게 한다.
--
-- ── 왜 image_url 을 재사용하지 않는가 ────────────────────────
-- 주소 끝이 .mp4 인지 보고 갈라도 동작은 한다. 다만 그러면 이미지와 영상이
-- 배타적이 되어 포스터(대기 화면)를 둘 수 없다. 영상은 로딩에 몇 초가 걸리고,
-- 그동안 화면이 비어 있으면 첫인상이 검은 화면이 된다.
-- 재생이 막히거나 실패했을 때 보여줄 것도 필요하다 —
-- 브라우저는 데이터 절약 모드나 저전력 모드에서 자동재생을 거부한다.
--
-- 그래서 video_url 을 따로 두고 image_url 은 포스터로 쓴다.
-- 기존 배너는 video_url 이 NULL 이라 지금과 똑같이 이미지로 나온다.
--
-- ── collation ────────────────────────────────────────────────
-- banners 는 운영에서 utf8mb4_unicode_ci 다. 컬럼만 추가하므로 테이블 기본값을
-- 그대로 따르지만, V22 가 collation 불일치로 서비스를 내렸던 적이 있어 명시해 둔다.
--
-- ── 재실행 가능해야 한다 ──────────────────────────────────────
-- 운영은 V14 에서 baseline 되어 마이그레이션 파일과 실제 스키마가 다르다.
-- MySQL 8 에는 ADD COLUMN IF NOT EXISTS 가 없으므로(MariaDB 문법이다)
-- information_schema 를 보고 없을 때만 실행한다.

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE banners ADD COLUMN video_url VARCHAR(700) NULL COMMENT ''배너 영상 주소 — 있으면 이미지 대신 재생, image_url 은 포스터로 쓰인다''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'banners' AND COLUMN_NAME = 'video_url'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
