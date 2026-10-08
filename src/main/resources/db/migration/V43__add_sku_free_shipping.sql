-- ============================================================
-- V43: 상품별 배송비 면제
-- ============================================================
--
-- 결제 테스트용 상품 등에 배송비를 붙이지 않으려고 관리자가 켜는 표시.
-- 주문에 담긴 상품이 모두 면제일 때만 배송비가 0원이 된다.

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE skus ADD COLUMN free_shipping TINYINT(1) NOT NULL DEFAULT 0',
    'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skus' AND COLUMN_NAME = 'free_shipping');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
