-- 분류에 영문 이름을 둔다.
--
-- 홈 섹션 머리말이 "+ ART TOY" 처럼 영문으로 나가는데, 지금은 분류 코드를
-- 그대로 쓰고 있다. 코드는 한글 이름으로 만들 수 없을 때 SUB_2 처럼 순번이
-- 붙는 내부 값이라, 이름을 바꿔도 머리말은 옛 코드로 남는다.
--
-- 영문명을 따로 두면 한글·영문을 각각 정할 수 있고, 둘 다 어드민에서 고친다.

ALTER TABLE sku_categories
    ADD COLUMN name_en VARCHAR(50) NULL
    COMMENT '영문 이름 — 홈 섹션 머리말 등에 쓴다. 비우면 한글 이름을 쓴다';

-- 지금 화면에 나가고 있는 값을 그대로 옮겨 둔다. 이 작업으로 보이는 것이
-- 달라지면 안 된다.
UPDATE sku_categories SET name_en = 'ART TOY'    WHERE code = 'ART_TOY';
UPDATE sku_categories SET name_en = 'SCULPTURE'  WHERE code = 'SCULPTURE';
UPDATE sku_categories SET name_en = 'CERAMIC'    WHERE code = 'CERAMIC';
UPDATE sku_categories SET name_en = 'LIMITED'    WHERE code = 'LIMITED';
