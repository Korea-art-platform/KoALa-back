-- V34: 주문자 정보(이름·이메일·전화)를 암호화로 옮기기 위한 자리 마련
--
-- 암호문은 원문보다 길다. AES-GCM(IV 12B + 태그 16B)을 Base64 로 담고 "enc:" 를
-- 붙이므로, 30자 전화번호도 100자 가까이 된다. 칼럼을 먼저 넓히지 않으면
-- 암호화를 켜는 순간 저장이 실패한다.
--
-- 암호문은 같은 값이라도 매번 달라져(IV 가 매번 새로) = 비교·검색이 되지 않는다.
-- 그래서 찾기용으로 HMAC 해시 칼럼을 따로 둔다.
--   orderer_email_hash  : 가입할 때 같은 이메일의 비회원 주문을 붙이는 데 쓴다
--   orderer_phone_hash  : 어드민에서 전화번호 전체로 찾을 때 쓴다
--   orderer_phone_last4 : 어드민에서 뒷자리 4자리로 찾을 때 쓴다 (평문이지만 4자리만)
--
-- 이 단계에서는 칼럼만 만든다. 기존 행은 애플리케이션이 기동하며 채운다.

ALTER TABLE orders
    MODIFY COLUMN orderer_name  VARCHAR(512) NOT NULL,
    MODIFY COLUMN orderer_email VARCHAR(512) NOT NULL,
    MODIFY COLUMN orderer_phone VARCHAR(512) NOT NULL;

ALTER TABLE orders
    ADD COLUMN orderer_email_hash  VARCHAR(64) NULL COMMENT '이메일 HMAC — 찾기용',
    ADD COLUMN orderer_phone_hash  VARCHAR(64) NULL COMMENT '전화번호 HMAC — 찾기용',
    ADD COLUMN orderer_phone_last4 VARCHAR(4)  NULL COMMENT '전화번호 뒷자리 4자리 — 찾기용';

CREATE INDEX idx_orders_orderer_email_hash  ON orders (orderer_email_hash);
CREATE INDEX idx_orders_orderer_phone_hash  ON orders (orderer_phone_hash);
CREATE INDEX idx_orders_orderer_phone_last4 ON orders (orderer_phone_last4);
