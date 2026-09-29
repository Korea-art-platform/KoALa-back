-- ============================================================
-- V38: 히어로 배너 꾸미는 방식
-- ============================================================
--
-- COMPOSED — 작품 누끼와 구성 이미지를 화면이 조립한다 (지금까지의 방식, 기본값)
-- FULL     — 디자인이 끝난 배너 한 장을 그대로 건다. 작품 연결은 그대로 두어
--            가격과 쇼핑하기 버튼은 계속 쓴다.
--
-- 재실행 가능하게 없을 때만 추가한다.

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE banners ADD COLUMN layout_mode VARCHAR(20) NOT NULL DEFAULT ''COMPOSED'' COMMENT ''히어로 꾸미는 방식 — COMPOSED(조립) · FULL(완성 이미지)''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'banners' AND COLUMN_NAME = 'layout_mode'
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
