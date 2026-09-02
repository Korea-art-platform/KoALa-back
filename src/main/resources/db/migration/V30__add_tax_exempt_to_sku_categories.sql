-- 대분류에 면세 표시를 단다.
--
-- 미술품 원작은 부가세를 붙이지 않고, 한정판·오픈에디션 같은 나머지 상품에는
-- 10% 를 붙여 판다. 어느 분류가 면세인지는 세무 판단이라 코드에 박아 두면
-- 분류가 늘어날 때마다 배포해야 한다. 분류에 붙여 두고 어드민에서 켜고 끈다.
--
-- 분류 코드(MAIN_2, MAIN_3…)는 이름으로 만들 수 없을 때 자동으로 붙는 값이라
-- 코드만 보고는 무엇인지 알 수 없다. 그래서 코드가 아니라 이 표시를 기준으로
-- 삼는다.

ALTER TABLE sku_categories
    ADD COLUMN tax_exempt BOOLEAN NOT NULL DEFAULT FALSE
    COMMENT '면세 분류 여부 — 참이면 부가세를 붙이지 않는다';

-- 지금 면세인 것은 원작 하나다.
UPDATE sku_categories
   SET tax_exempt = TRUE
 WHERE type = 'MAIN' AND name = '원작';
