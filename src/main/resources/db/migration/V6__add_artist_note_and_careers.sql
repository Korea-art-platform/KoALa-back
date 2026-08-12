-- ============================================================
-- V6: 작가노트(artist_note) 컬럼 추가 + 작가 약력 테이블 신규 생성
-- ============================================================

-- 1. artists 테이블에 artist_note 컬럼 추가
ALTER TABLE artists
    ADD COLUMN artist_note LONGTEXT NULL AFTER description;

-- 2. artist_careers 테이블 신규 생성
CREATE TABLE IF NOT EXISTS artist_careers (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    artist_id  BIGINT NOT NULL,
    category   VARCHAR(20)     NOT NULL,   -- '학력' | '개인전' | '그룹전'
    year       INT             NOT NULL,
    content    VARCHAR(500)    NOT NULL,
    sort_order INT             NOT NULL DEFAULT 0,
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    KEY idx_artist_careers_artist_id (artist_id),
    KEY idx_artist_careers_category  (category),

    CONSTRAINT fk_artist_careers_artist
        FOREIGN KEY (artist_id) REFERENCES artists (id) ON DELETE CASCADE,
    CONSTRAINT ck_artist_careers_category
        CHECK (category IN ('학력', '개인전', '그룹전'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
