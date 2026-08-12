-- ============================================================
-- V8: 반품/교환 요청 테이블 생성
-- ============================================================

CREATE TABLE IF NOT EXISTS return_requests (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    return_no      VARCHAR(40)     NOT NULL UNIQUE COMMENT '반품 고유 번호',
    order_id       BIGINT NOT NULL,
    user_id        BIGINT NOT NULL,
    return_type    VARCHAR(20)     NOT NULL COMMENT 'RETURN | EXCHANGE',
    reason         VARCHAR(30)     NOT NULL COMMENT 'SIMPLE_CHANGE | DEFECT | WRONG_DELIVERY | OTHER',
    reason_detail  TEXT            DEFAULT NULL COMMENT '상세 사유',
    status         VARCHAR(20)     NOT NULL DEFAULT 'REQUESTED' COMMENT 'REQUESTED | APPROVED | REJECTED | COMPLETED',
    refund_amount  DECIMAL(13, 2)  DEFAULT NULL COMMENT '승인 시 환불 금액',
    admin_memo     TEXT            DEFAULT NULL COMMENT '관리자 처리 메모',
    processed_at   DATETIME        DEFAULT NULL COMMENT '처리(승인/거절) 일시',
    created_at     DATETIME        NOT NULL,
    updated_at     DATETIME        NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_return_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_return_user  FOREIGN KEY (user_id)  REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
