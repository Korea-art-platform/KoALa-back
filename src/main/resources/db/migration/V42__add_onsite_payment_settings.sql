-- ============================================================
-- V42: 현장결제 페이지 설정
-- ============================================================
--
-- 관리자 계정 없이 쓰는 현장결제 페이지(/onsite)를 켜고 끄고, 들어갈 때 넣는
-- PIN 을 둔다. 한 줄(id = 1)만 쓴다.
--
-- enabled  — 꺼 두면 PIN 이 맞아도 결제를 만들 수 없다
-- pin_hash — PIN 은 BCrypt 로만 남긴다
-- version  — 끄거나 PIN 을 바꿀 때마다 올린다. 이전에 받은 입장권은 버전이 달라 바로 막힌다

CREATE TABLE IF NOT EXISTS onsite_payment_settings (
    id          BIGINT        NOT NULL,
    enabled     TINYINT(1)    NOT NULL DEFAULT 0,
    pin_hash    VARCHAR(100)  NULL,
    version     INT           NOT NULL DEFAULT 0,
    updated_at  DATETIME(6)   NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO onsite_payment_settings (id, enabled, pin_hash, version, updated_at)
VALUES (1, 0, NULL, 0, NULL);
