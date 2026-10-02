-- ============================================================
-- V41: 현장결제
-- ============================================================
--
-- order_channel — 주문이 들어온 길. ONLINE(사이트 주문, 기본값) · ON_SITE(현장결제)
-- pay_token     — 현장결제 링크·QR 에 싣는 추측할 수 없는 값. 주문번호는 시각으로
--                 만들어져 앞자리를 짐작할 수 있어 링크에 그대로 쓰지 않는다.
--
-- 재실행 가능하게 없을 때만 추가한다.

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE orders ADD COLUMN order_channel VARCHAR(20) NOT NULL DEFAULT ''ONLINE'' COMMENT ''주문 경로 — ONLINE · ON_SITE''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'order_channel'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE orders ADD COLUMN pay_token VARCHAR(40) NULL COMMENT ''현장결제 링크 토큰''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'pay_token'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'CREATE UNIQUE INDEX uk_orders_pay_token ON orders (pay_token)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'uk_orders_pay_token'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'CREATE INDEX idx_orders_channel_created ON orders (order_channel, created_at)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'idx_orders_channel_created'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
