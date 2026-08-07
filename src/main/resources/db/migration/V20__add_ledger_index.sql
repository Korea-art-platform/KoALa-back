-- ============================================================
-- V20: sku_stock_ledger 재고 집계 인덱스
-- ============================================================
--
-- 재고는 원장(delta) SUM 으로 계산한다.
--   단건:  SELECT COALESCE(SUM(delta),0) FROM sku_stock_ledger WHERE sku_id = ?
--   목록:  SELECT sku_id, COALESCE(SUM(delta),0) ... WHERE sku_id IN (...) GROUP BY sku_id
--
-- 지금까지 sku_id 에 인덱스가 없어 두 쿼리 모두 풀스캔(type=ALL)이었고,
-- 원장 행이 쌓일수록 주문/목록 조회가 같이 느려진다.
--
-- delta 를 인덱스에 포함해 커버링 인덱스로 만든다.
-- SUM 대상 컬럼까지 인덱스에서 읽히므로 클러스터드 인덱스 조회가 사라진다(Using index).
-- 선두 컬럼이 sku_id 이므로 sku_id 단독 조회에도 그대로 쓰인다.
--
-- 10만 행 기준 측정(로컬 MySQL 8.0):
--   단건 SUM : 14.65ms → 0.10ms  (type=ALL rows=99964 → type=ref rows=200, Using index)
--   목록 배치: 15.86ms → 1.80ms  (type=ALL rows=96196 → type=range, Using index)

CREATE INDEX idx_sku_stock_ledger_sku_id
    ON sku_stock_ledger (sku_id, delta);
