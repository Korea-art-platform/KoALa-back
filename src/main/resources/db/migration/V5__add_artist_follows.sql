-- ============================================================
-- V5: 아티스트 팔로우 테이블
-- ============================================================

CREATE TABLE IF NOT EXISTS artist_follows (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    user_id    BIGINT NOT NULL,
    artist_id  BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    -- 한 유저가 같은 아티스트를 중복 팔로우 불가
    UNIQUE KEY uq_follow (user_id, artist_id),
    -- 아티스트별 팔로워 수 조회 최적화
    INDEX idx_follow_artist (artist_id),
    -- 유저별 팔로우 목록 조회 최적화
    INDEX idx_follow_user   (user_id),

    CONSTRAINT fk_follow_user   FOREIGN KEY (user_id)   REFERENCES users(id)   ON DELETE CASCADE,
    CONSTRAINT fk_follow_artist FOREIGN KEY (artist_id) REFERENCES artists(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
