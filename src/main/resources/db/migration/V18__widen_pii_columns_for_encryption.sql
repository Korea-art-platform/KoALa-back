-- PII 컬럼 AES-256-GCM 암호화 대응
-- 암호문("enc:" + Base64(IV+cipher+tag))은 평문보다 길이가 ~4배(한글 UTF-8 + Base64)로 늘어나므로
-- 관련 컬럼을 넓히고, 암호문과 충돌하는 CHECK 제약(있을 경우)을 제거한다.
--
-- 주의: 운영 DB는 Hibernate(ddl-auto)가 스키마를 생성해 V1의 명명된 CHECK 제약(ck_users_phone)이
--       없을 수 있다. 따라서 무조건 DROP 하지 않고, 존재할 때만 제거하도록 조건부로 처리한다.

-- 1) users.phone 정규식 CHECK 제약이 "존재할 때만" 제거
SET @ddl := (
  SELECT IF(COUNT(*) > 0,
            'ALTER TABLE users DROP CHECK ck_users_phone',
            'DO 0')
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'users'
    AND CONSTRAINT_NAME = 'ck_users_phone'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 컬럼 확장 (암호문 수용)
-- users
ALTER TABLE users MODIFY COLUMN name  VARCHAR(512) NOT NULL;
ALTER TABLE users MODIFY COLUMN phone VARCHAR(512) NULL;

-- order_shipments
ALTER TABLE order_shipments MODIFY COLUMN recipient_name  VARCHAR(512)  NOT NULL;
ALTER TABLE order_shipments MODIFY COLUMN recipient_phone VARCHAR(512)  NOT NULL;
ALTER TABLE order_shipments MODIFY COLUMN zip_code        VARCHAR(512)  NOT NULL;
ALTER TABLE order_shipments MODIFY COLUMN address1        VARCHAR(1024) NOT NULL;
ALTER TABLE order_shipments MODIFY COLUMN address2        VARCHAR(1024) NULL;

-- user_addresses
ALTER TABLE user_addresses MODIFY COLUMN recipient_name  VARCHAR(512)  NOT NULL;
ALTER TABLE user_addresses MODIFY COLUMN recipient_phone VARCHAR(512)  NOT NULL;
ALTER TABLE user_addresses MODIFY COLUMN zip_code        VARCHAR(512)  NOT NULL;
ALTER TABLE user_addresses MODIFY COLUMN address1        VARCHAR(1024) NOT NULL;
ALTER TABLE user_addresses MODIFY COLUMN address2        VARCHAR(1024) NULL;
