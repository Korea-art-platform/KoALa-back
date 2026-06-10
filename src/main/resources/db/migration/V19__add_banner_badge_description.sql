-- 배너 히어로의 뱃지/부연 설명을 배너별로 관리하기 위한 컬럼 추가
ALTER TABLE banners ADD COLUMN badge       VARCHAR(100) NULL;
ALTER TABLE banners ADD COLUMN description VARCHAR(500) NULL;
