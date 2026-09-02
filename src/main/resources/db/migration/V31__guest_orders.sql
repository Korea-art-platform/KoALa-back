-- 비회원 주문을 받는다.
--
-- 지금은 주문에 반드시 회원이 붙어야 해서, 계정을 만들지 않으면 살 수 없다.
-- 회원 없이도 주문이 서도록 열어 준다.
--
-- 조회는 주문번호 + 휴대폰번호로 한다. 비회원에게는 로그인이 없으니 나중에
-- 자기 주문을 찾을 길이 이것뿐이다. 전화번호는 이미 orderer_phone 에 있으므로
-- 따로 두지 않는다.

ALTER TABLE orders
    MODIFY COLUMN user_id BIGINT NULL
    COMMENT '주문한 회원. 비회원 주문이면 비어 있다';

-- 비회원 주문 조회는 주문번호로 찾은 뒤 전화번호를 맞춰 본다.
-- order_no 에는 이미 유일 인덱스가 있어 따로 만들지 않는다.

-- 같은 이메일로 나중에 가입하면 그 주문을 계정에 붙인다. 가입 시 이메일로
-- 찾아야 하므로 인덱스를 둔다.
CREATE INDEX idx_orders_orderer_email_user
    ON orders (orderer_email, user_id);
