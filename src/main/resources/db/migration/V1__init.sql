-- ============================================================
-- V1: 전체 초기 스키마
-- ============================================================

CREATE TABLE artists (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    artist_code       VARCHAR(40)     NOT NULL,
    name              VARCHAR(150)    NOT NULL,
    slug              VARCHAR(180)    NOT NULL,
    description       LONGTEXT,
    profile_image_url VARCHAR(700),
    is_active         TINYINT(1)      NOT NULL DEFAULT 1,
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at        DATETIME(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_artists_artist_code (artist_code),
    UNIQUE KEY uk_artists_slug (slug),
    KEY idx_artists_name (name),
    KEY idx_artists_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE artist_media (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    artist_id     BIGINT       NOT NULL,
    sort_order    INT          NOT NULL,
    media_type    VARCHAR(20)  NOT NULL,
    media_role    VARCHAR(30)  NOT NULL,
    title         VARCHAR(200),
    file_url      VARCHAR(700) NOT NULL,
    thumbnail_url VARCHAR(700),
    meta_json     JSON,
    description   LONGTEXT,
    created_at    DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE artworks (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    artist_id    BIGINT      NOT NULL,
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    artwork_year INT         NOT NULL,
    price        INT         NOT NULL,
    width_mm     INT,
    height_mm    INT,
    sale_type    VARCHAR(20) NOT NULL,
    status       VARCHAR(20) NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE skus (
    id                    BIGINT  NOT NULL AUTO_INCREMENT,
    sku_code              VARCHAR(40)      NOT NULL,
    artist_id             BIGINT  NOT NULL,
    name                  VARCHAR(200)     NOT NULL,
    slug                  VARCHAR(220)     NOT NULL,
    description           LONGTEXT,
    sku_type              VARCHAR(20)      NOT NULL DEFAULT 'ARTWORK',
    genre                 VARCHAR(50)      NOT NULL DEFAULT 'ART_TOY',
    currency              CHAR(3)          NOT NULL DEFAULT 'KRW',
    list_price            DECIMAL(13,2)    NOT NULL DEFAULT 0.00,
    sale_price            DECIMAL(13,2),
    is_limited_edition    TINYINT(1)       NOT NULL DEFAULT 0,
    edition_size          INT,
    edition_number        INT,
    primary_image_url     VARCHAR(700),
    ar_asset_url          VARCHAR(700),
    ar_preview_image_url  VARCHAR(700),
    spine_pictures_json   JSON,
    width_cm              DECIMAL(10,2),
    height_cm             DECIMAL(10,2),
    depth_cm              DECIMAL(10,2),
    weight_kg             DECIMAL(10,3),
    status                VARCHAR(20)      NOT NULL DEFAULT 'DRAFT',
    published_at          DATETIME(3),
    created_at            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at            DATETIME(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_skus_sku_code (sku_code),
    UNIQUE KEY uk_skus_slug (slug),
    KEY idx_skus_artist_id (artist_id),
    KEY idx_skus_status (status),
    KEY idx_skus_genre (genre),
    KEY idx_skus_published_at (published_at),
    CONSTRAINT fk_skus_artist FOREIGN KEY (artist_id) REFERENCES artists (id) ON DELETE RESTRICT,
    CONSTRAINT ck_skus_type   CHECK (sku_type IN ('ARTWORK','GOODS')),
    CONSTRAINT ck_skus_status CHECK (status IN ('DRAFT','ACTIVE','OUT_OF_STOCK','DISCONTINUED')),
    CONSTRAINT ck_skus_prices CHECK (list_price >= 0 AND (sale_price IS NULL OR (sale_price >= 0 AND sale_price <= list_price))),
    CONSTRAINT ck_skus_edition CHECK (
        (is_limited_edition = FALSE AND edition_size IS NULL AND edition_number IS NULL) OR
        (is_limited_edition = TRUE  AND edition_size IS NOT NULL AND edition_size > 0
                                    AND edition_number IS NOT NULL AND edition_number > 0
                                    AND edition_number <= edition_size)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sku_media (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    sku_id        BIGINT        NOT NULL,
    sort_order    INT           NOT NULL,
    is_primary    BIT(1)        NOT NULL,
    media_type    VARCHAR(20)   NOT NULL,
    media_role    VARCHAR(30)   NOT NULL,
    file_url      VARCHAR(700)  NOT NULL,
    thumbnail_url VARCHAR(700),
    alt_text      VARCHAR(255),
    angle_degree  DECIMAL(6,2),
    meta_json     JSON,
    created_at    DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sku_stock_ledger (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    sku_id     BIGINT       NOT NULL,
    delta      INT          NOT NULL,
    reason     VARCHAR(30)  NOT NULL,
    ref_type   VARCHAR(30),
    ref_id     BIGINT,
    memo       VARCHAR(200),
    created_at DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    user_code       VARCHAR(40)     NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    phone           VARCHAR(30),
    oauth_provider  VARCHAR(20),
    oauth_id        VARCHAR(100),
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    last_login_at   DATETIME(3),
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at      DATETIME(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_user_code (user_code),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_status (status),
    KEY idx_users_oauth (oauth_provider, oauth_id),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED')),
    CONSTRAINT ck_users_phone  CHECK (phone IS NULL OR phone REGEXP '^\\+[1-9][0-9]{6,14}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_addresses (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,
    label            VARCHAR(50),
    recipient_name   VARCHAR(100) NOT NULL,
    recipient_phone  VARCHAR(30)  NOT NULL,
    zip_code         VARCHAR(20)  NOT NULL,
    address1         VARCHAR(255) NOT NULL,
    address2         VARCHAR(255),
    is_default       BIT(1)       NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE carts (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    currency   VARCHAR(3)  NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_carts_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE cart_items (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    cart_id              BIGINT        NOT NULL,
    sku_id               BIGINT        NOT NULL,
    quantity             INT           NOT NULL,
    unit_price           DECIMAL(13,2) NOT NULL,
    option_snapshot_json JSON,
    added_at             DATETIME(6),
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY fk_cart_items_cart (cart_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE orders (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    order_no         VARCHAR(40)     NOT NULL,
    user_id          BIGINT NOT NULL,
    order_status     VARCHAR(30)     NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_status   VARCHAR(30)     NOT NULL DEFAULT 'READY',
    currency         CHAR(3)         NOT NULL DEFAULT 'KRW',
    product_amount   DECIMAL(13,2)   NOT NULL DEFAULT 0.00,
    discount_amount  DECIMAL(13,2)   NOT NULL DEFAULT 0.00,
    shipping_amount  DECIMAL(13,2)   NOT NULL DEFAULT 0.00,
    tax_amount       DECIMAL(13,2)   NOT NULL DEFAULT 0.00,
    total_amount     DECIMAL(13,2)   NOT NULL DEFAULT 0.00,
    orderer_name     VARCHAR(100)    NOT NULL,
    orderer_email    VARCHAR(255)    NOT NULL,
    orderer_phone    VARCHAR(30)     NOT NULL,
    paid_at          DATETIME(3),
    cancelled_at     DATETIME(3),
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_no (order_no),
    KEY idx_orders_user_id (user_id),
    KEY idx_orders_order_status (order_status),
    KEY idx_orders_payment_status (payment_status),
    KEY idx_orders_created_at (created_at),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_orders_amounts       CHECK (product_amount >= 0 AND discount_amount >= 0 AND shipping_amount >= 0 AND tax_amount >= 0 AND total_amount >= 0),
    CONSTRAINT ck_orders_order_status  CHECK (order_status   IN ('PENDING_PAYMENT','PAID','PREPARING','SHIPPED','DELIVERED','CANCELLED','REFUNDED','PARTIAL_REFUNDED')),
    CONSTRAINT ck_orders_payment_status CHECK (payment_status IN ('READY','PAID','FAILED','CANCELLED','REFUNDED','PARTIAL_REFUNDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_items (
    id                    BIGINT NOT NULL AUTO_INCREMENT,
    order_id              BIGINT NOT NULL,
    sku_id                BIGINT,
    artist_id             BIGINT,
    sku_code_snapshot     VARCHAR(40)     NOT NULL,
    artist_code_snapshot  VARCHAR(40),
    sku_name_snapshot     VARCHAR(200)    NOT NULL,
    artist_name_snapshot  VARCHAR(150),
    quantity              INT    NOT NULL DEFAULT 1,
    unit_price            DECIMAL(13,2)   NOT NULL,
    discount_amount       DECIMAL(13,2)   NOT NULL DEFAULT 0.00,
    tax_amount            DECIMAL(13,2)   NOT NULL DEFAULT 0.00,
    line_total_amount     DECIMAL(13,2)   NOT NULL,
    sku_snapshot_json     JSON,
    review_written        TINYINT(1)      NOT NULL DEFAULT 0,
    created_at            DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_order_items_order_id (order_id),
    KEY idx_order_items_sku_id (sku_id),
    KEY idx_order_items_artist_id (artist_id),
    KEY idx_order_items_review_written (review_written),
    CONSTRAINT fk_order_items_order  FOREIGN KEY (order_id)  REFERENCES orders  (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_sku    FOREIGN KEY (sku_id)    REFERENCES skus    (id) ON DELETE SET NULL,
    CONSTRAINT fk_order_items_artist FOREIGN KEY (artist_id) REFERENCES artists (id) ON DELETE SET NULL,
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_amounts  CHECK (unit_price >= 0 AND discount_amount >= 0 AND tax_amount >= 0 AND line_total_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_shipments (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    order_id         BIGINT       NOT NULL,
    recipient_name   VARCHAR(100) NOT NULL,
    recipient_phone  VARCHAR(30)  NOT NULL,
    zip_code         VARCHAR(20)  NOT NULL,
    address1         VARCHAR(255) NOT NULL,
    address2         VARCHAR(255),
    carrier_code     VARCHAR(50),
    tracking_no      VARCHAR(100),
    delivery_request VARCHAR(255),
    shipped_at       DATETIME(6),
    delivered_at     DATETIME(6),
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_shipments_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payments (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    order_id           BIGINT        NOT NULL,
    payment_no         VARCHAR(40)   NOT NULL,
    provider           VARCHAR(30)   NOT NULL,
    method             VARCHAR(30)   NOT NULL,
    status             VARCHAR(30)   NOT NULL,
    currency           VARCHAR(3)    NOT NULL,
    requested_amount   DECIMAL(13,2) NOT NULL,
    approved_amount    DECIMAL(13,2) NOT NULL,
    cancelled_amount   DECIMAL(13,2) NOT NULL,
    pg_transaction_id  VARCHAR(100),
    approval_no        VARCHAR(100),
    failure_code       VARCHAR(100),
    failure_message    VARCHAR(255),
    raw_response_json  JSON,
    approved_at        DATETIME(6),
    cancelled_at       DATETIME(6),
    failed_at          DATETIME(6),
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_payment_no (payment_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment_events (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    payment_id        BIGINT        NOT NULL,
    event_type        VARCHAR(30)   NOT NULL,
    event_status      VARCHAR(20)   NOT NULL,
    amount            DECIMAL(13,2) NOT NULL,
    provider_event_id VARCHAR(100),
    payload_json      JSON,
    created_at        DATETIME(6),
    PRIMARY KEY (id),
    KEY fk_payment_events_payment (payment_id),
    CONSTRAINT fk_payment_events_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sku_reviews (
    id                    BIGINT      NOT NULL AUTO_INCREMENT,
    sku_id                BIGINT      NOT NULL,
    user_id               BIGINT      NOT NULL,
    order_id              BIGINT      NOT NULL,
    order_item_id         BIGINT      NOT NULL,
    review_code           VARCHAR(40) NOT NULL,
    review_status         VARCHAR(20) NOT NULL,
    rating                INT         NOT NULL,
    title                 VARCHAR(200),
    content               TINYTEXT    NOT NULL,
    is_featured           BIT(1)      NOT NULL,
    is_visible            BIT(1)      NOT NULL,
    like_count            INT         NOT NULL,
    report_count          INT         NOT NULL,
    admin_memo            VARCHAR(500),
    moderated_by_admin_id BIGINT,
    moderated_at          DATETIME(6),
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    deleted_at            DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku_reviews_order_item_id (order_item_id),
    UNIQUE KEY uk_sku_reviews_review_code (review_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sku_review_media (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    review_id     BIGINT       NOT NULL,
    sort_order    INT          NOT NULL,
    media_type    VARCHAR(20)  NOT NULL,
    file_url      VARCHAR(700) NOT NULL,
    thumbnail_url VARCHAR(700),
    created_at    DATETIME(6),
    PRIMARY KEY (id),
    KEY fk_sku_review_media_review (review_id),
    CONSTRAINT fk_sku_review_media_review FOREIGN KEY (review_id) REFERENCES sku_reviews (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sku_review_stats (
    sku_id       BIGINT        NOT NULL,
    review_count INT           NOT NULL,
    rating_sum   BIGINT        NOT NULL,
    avg_rating   DECIMAL(3,2)  NOT NULL,
    updated_at   DATETIME(6),
    PRIMARY KEY (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE wishlist_items (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    sku_id     BIGINT      NOT NULL,
    created_at DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE banners (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    banner_code         VARCHAR(40)  NOT NULL,
    banner_type         VARCHAR(30)  NOT NULL,
    title               VARCHAR(200) NOT NULL,
    subtitle            VARCHAR(255),
    image_url           VARCHAR(700) NOT NULL,
    mobile_image_url    VARCHAR(700),
    link_url            VARCHAR(700),
    link_target         VARCHAR(20)  NOT NULL,
    bg_color            VARCHAR(30),
    text_color          VARCHAR(30),
    sort_order          INT          NOT NULL,
    is_active           BIT(1)       NOT NULL,
    visible_from        DATETIME(6),
    visible_to          DATETIME(6),
    created_by_admin_id BIGINT,
    updated_by_admin_id BIGINT,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    deleted_at          DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_banners_banner_code (banner_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE admin_roles (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    role_code   VARCHAR(50)  NOT NULL,
    role_name   VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    is_active   BIT(1)       NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_roles_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE admins (
    id                   BIGINT NOT NULL AUTO_INCREMENT,
    admin_code           VARCHAR(40)     NOT NULL,
    login_id             VARCHAR(50)     NOT NULL,
    password_hash        VARCHAR(255)    NOT NULL,
    name                 VARCHAR(100)    NOT NULL,
    email                VARCHAR(150),
    phone                VARCHAR(30),
    status               VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    login_fail_count     INT             NOT NULL DEFAULT 0,
    last_login_at        DATETIME(3),
    last_login_ip        VARCHAR(45),
    password_changed_at  DATETIME(3),
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at           DATETIME(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_admins_admin_code (admin_code),
    UNIQUE KEY uk_admins_login_id (login_id),
    UNIQUE KEY uk_admins_email (email),
    KEY idx_admins_status (status),
    KEY idx_admins_created_at (created_at),
    CONSTRAINT ck_admins_status CHECK (status IN ('ACTIVE','INACTIVE','LOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE admin_role_mappings (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    admin_id   BIGINT      NOT NULL,
    role_id    BIGINT      NOT NULL,
    created_at DATETIME(6),
    PRIMARY KEY (id),
    KEY fk_admin_role_mappings_role (role_id),
    CONSTRAINT fk_admin_role_mappings_role FOREIGN KEY (role_id) REFERENCES admin_roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE admin_audit_logs (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    admin_id     BIGINT,
    target_id    BIGINT,
    action_type  VARCHAR(50)  NOT NULL,
    target_type  VARCHAR(50)  NOT NULL,
    http_method  VARCHAR(10),
    request_path VARCHAR(255),
    ip_address   VARCHAR(45),
    user_agent   VARCHAR(500),
    memo         VARCHAR(500),
    before_data  JSON,
    after_data   JSON,
    created_at   DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE password_reset_token (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    email      VARCHAR(255) NOT NULL,
    token      VARCHAR(6)   NOT NULL,
    is_used    TINYINT(1)   NOT NULL DEFAULT 0,
    expired_at DATETIME     NOT NULL,
    created_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_prt_email       (email),
    INDEX idx_prt_email_token (email, token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
