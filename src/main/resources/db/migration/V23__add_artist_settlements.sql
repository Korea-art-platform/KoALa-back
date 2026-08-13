-- ============================================================
-- V23: 작가 정산 — 수수료율 + 월별 정산 스냅샷
-- ============================================================
--
-- 지금까지 작가 대금 정산은 코드에 아무것도 없었다. 필요한 원자료는 이미 다 있다 —
-- order_items 에 artist_id 와 line_total_amount 가 들어 있다. 없는 것은 둘뿐이다.
--   ① 작가별 수수료율
--   ② "이 달치는 얼마로 확정했고 지급했는가" 라는 기록
--
-- ── 왜 계산 결과를 저장하는가 ──────────────────────────────────
-- 매번 다시 계산하면 지난달 정산액이 오늘 바뀔 수 있다. 반품이 뒤늦게 승인되거나
-- 수수료율을 조정하면 이미 지급한 달의 숫자가 조용히 달라진다.
-- 그래서 확정(CONFIRMED)하는 순간의 값을 스냅샷으로 굳힌다.
-- 확정 전에는 행이 없고, 조회할 때마다 그때그때 계산해서 보여준다.
--
-- ── COLLATE 를 명시하는 이유 ──────────────────────────────────
-- 이 DB 는 테이블마다 collation 이 다르다. 운영 테이블 대부분은 utf8mb4_unicode_ci 인데,
-- V21 이 만든 sku_categories 만 utf8mb4_0900_ai_ci 라서 V22 에서 문자열 비교가
-- "Illegal mix of collations" 로 터졌고 서비스가 내려갔다.
-- 주변 테이블(artists · orders)에 맞춰 utf8mb4_unicode_ci 로 만든다.
--
-- ── 재실행 가능해야 한다 ──────────────────────────────────────
-- 운영은 V14 에서 baseline 되어 마이그레이션 파일과 실제 스키마가 일치하지 않는다.
-- 모든 변경을 "없을 때만" 으로 감싼다.

-- ── ① 작가 수수료율 ──────────────────────────────────────────
-- 판매가에서 플랫폼이 갖는 비율. 0.2000 = 20%.
-- 기본값을 두는 이유는 기존 작가 행에도 값이 채워져야 계산이 되기 때문이다.
-- 실제 비율은 계약에 따라 작가별로 어드민에서 조정한다.
SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE artists ADD COLUMN commission_rate DECIMAL(5,4) NOT NULL DEFAULT 0.2000 COMMENT ''플랫폼 수수료율 (0.2000 = 20%)''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'artists' AND COLUMN_NAME = 'commission_rate'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ── ② 정산 스냅샷 ────────────────────────────────────────────
-- 금액은 전부 DECIMAL 이다. 돈을 부동소수로 다루면 합계가 1원씩 어긋난다.
--
-- payout_amount 를 따로 저장하는 이유:
--   계산식은 바뀔 수 있지만 "얼마를 보냈는가" 는 바뀌면 안 된다.
--   gross - refund - commission 을 매번 다시 계산하면 그 기록이 사라진다.
CREATE TABLE IF NOT EXISTS artist_settlements (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    artist_id         BIGINT       NOT NULL,
    period_ym         CHAR(7)      NOT NULL COMMENT '정산 월 (YYYY-MM)',

    gross_amount      DECIMAL(13,2) NOT NULL COMMENT '해당 월 배송완료 매출',
    refund_amount     DECIMAL(13,2) NOT NULL DEFAULT 0 COMMENT '해당 월 반품 승인분 차감',
    commission_rate   DECIMAL(5,4)  NOT NULL COMMENT '확정 시점의 수수료율',
    commission_amount DECIMAL(13,2) NOT NULL COMMENT '플랫폼 몫',
    payout_amount     DECIMAL(13,2) NOT NULL COMMENT '작가에게 지급할 금액',

    status            VARCHAR(20)   NOT NULL DEFAULT 'CONFIRMED' COMMENT 'CONFIRMED / PAID',
    confirmed_at      DATETIME(6)   NOT NULL,
    paid_at           DATETIME(6)   DEFAULT NULL,
    memo              VARCHAR(500)  DEFAULT NULL,

    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),
    -- 같은 달을 두 번 확정할 수 없다. 이게 없으면 이중 지급이 난다
    UNIQUE KEY uk_artist_settlements_artist_period (artist_id, period_ym),
    KEY idx_artist_settlements_period (period_ym, status),
    CONSTRAINT fk_artist_settlements_artist FOREIGN KEY (artist_id) REFERENCES artists (id),
    CONSTRAINT ck_artist_settlements_status CHECK (status IN ('CONFIRMED', 'PAID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
