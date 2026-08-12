-- ============================================================
-- 운영 DB 스키마 스냅샷 (koala) — 2026-08-12 기준
-- ============================================================
--
-- 자동 생성 파일이다. 직접 고치지 말 것.
--   sudo docker exec koala-mysql sh -c 'mysqldump -uroot -p$MYSQL_ROOT_PASSWORD --no-data koala'
--
-- ── 왜 이 파일이 있나 ────────────────────────────────────────
-- 운영 DB 는 V14 에서 baseline 되어 V1~V14 가 실행된 적이 없다.
-- 초기 테이블은 마이그레이션이 아니라 Hibernate 가 만들었다
-- (FK 이름이 FK5nwgm26jl51iu2nxqd4m7djb9 인 것이 그 흔적이다).
--
-- 그래서 마이그레이션 파일을 읽고 "운영도 이렇겠지" 하고 넘겨짚으면 틀린다.
-- 실제로 V21 이 V1 에만 있던 ck_skus_edition 을 지우려다 운영 배포가 실패했고,
-- 사이트가 내려갔다.
--
-- ── 새 마이그레이션을 쓸 때 ─────────────────────────────────
-- 1. 건드릴 테이블·컬럼·제약·인덱스가 여기 있는지 먼저 확인한다.
-- 2. 여기 없는 것을 DROP 하지 않는다. 지워야 하면 있을 때만 지우도록 감싼다.
-- 3. MySQL 은 DDL 을 롤백하지 않는다. 중간에 실패하면 앞부분은 적용된 채로 남으므로,
--    스크립트는 몇 번을 돌려도 결과가 같아야 한다.
--
-- 참고: 운영과 마이그레이션 체인의 결과는 아직 완전히 같지 않다.
--   운영(Hibernate 생성)  datetime(6), bit(1)
--   체인                  datetime(3), tinyint(1)
-- 동작에는 영향이 없어 그대로 두었다. 맞추려면 별도 작업이 필요하다.
-- ============================================================

CREATE TABLE `admin_audit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `after_data` json DEFAULT NULL,
  `before_data` json DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `http_method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ip_address` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `memo` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_id` bigint DEFAULT NULL,
  `target_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `admin_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKdyxmnm5xoijha7uk14uv86vmr` (`admin_id`),
  CONSTRAINT `FKdyxmnm5xoijha7uk14uv86vmr` FOREIGN KEY (`admin_id`) REFERENCES `admins` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `admin_role_mappings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `admin_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKnau22kxse65gfr37egxcjqih9` (`admin_id`),
  KEY `FK7px1x8kfb34mtbfkik9oh3bho` (`role_id`),
  CONSTRAINT `FK7px1x8kfb34mtbfkik9oh3bho` FOREIGN KEY (`role_id`) REFERENCES `admin_roles` (`id`),
  CONSTRAINT `FKnau22kxse65gfr37egxcjqih9` FOREIGN KEY (`admin_id`) REFERENCES `admins` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `admin_roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `role_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK3ti3qx9x4fcrig5wi4f90nkfd` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `admins` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `admin_code` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `email` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_login_at` datetime(6) DEFAULT NULL,
  `last_login_ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `login_fail_count` int NOT NULL,
  `login_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_changed_at` datetime(6) DEFAULT NULL,
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKhuj3mnt7e1f1dwghu6g9nktfe` (`admin_code`),
  UNIQUE KEY `UK3ctrm37nusjtpirk0wl0mmgw5` (`login_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `artist_careers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `sort_order` int NOT NULL,
  `year` int DEFAULT NULL,
  `artist_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKpaa44ntalmyiisudft1u5h1pd` (`artist_id`),
  CONSTRAINT `FKpaa44ntalmyiisudft1u5h1pd` FOREIGN KEY (`artist_id`) REFERENCES `artists` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `artist_follows` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `artist_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_follow` (`user_id`,`artist_id`),
  KEY `FK4lyal31dfs4obiucx5ou8cj0i` (`artist_id`),
  CONSTRAINT `FK4lyal31dfs4obiucx5ou8cj0i` FOREIGN KEY (`artist_id`) REFERENCES `artists` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `artist_media` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `description` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `file_url` varchar(700) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `media_role` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `media_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `meta_json` json DEFAULT NULL,
  `sort_order` int NOT NULL,
  `thumbnail_url` varchar(700) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `artist_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKa0s0vcjir12uiakgffhw9lk3a` (`artist_id`),
  CONSTRAINT `FKa0s0vcjir12uiakgffhw9lk3a` FOREIGN KEY (`artist_id`) REFERENCES `artists` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `artists` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `artist_code` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `artist_note` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `deleted_at` datetime(6) DEFAULT NULL,
  `description` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `featured_sku_id` bigint unsigned DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `name` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `profile_image_url` varchar(700) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `slug` varchar(180) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK2leocfw4ra2hpphkmoigtkci4` (`artist_code`),
  UNIQUE KEY `UKgfgrdjnnf1k7w6kjss7ghfltq` (`slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `artworks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `artist_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `height_mm` int DEFAULT NULL,
  `price` int NOT NULL,
  `sale_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `width_mm` int DEFAULT NULL,
  `artwork_year` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `banners` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `banner_code` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `banner_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `bg_color` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `image_url` varchar(700) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `link_target` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `link_url` varchar(700) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `mobile_image_url` varchar(700) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int NOT NULL,
  `subtitle` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `text_color` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `visible_from` datetime(6) DEFAULT NULL,
  `visible_to` datetime(6) DEFAULT NULL,
  `created_by_admin_id` bigint DEFAULT NULL,
  `updated_by_admin_id` bigint DEFAULT NULL,
  `badge` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK2mkpus3wsowue35xt3imty10h` (`banner_code`),
  KEY `FK49nrwpsqemyu82l3ejo212d0v` (`created_by_admin_id`),
  KEY `FKn6g3tn9mbtoy101rymjcclhgy` (`updated_by_admin_id`),
  CONSTRAINT `FK49nrwpsqemyu82l3ejo212d0v` FOREIGN KEY (`created_by_admin_id`) REFERENCES `admins` (`id`),
  CONSTRAINT `FKn6g3tn9mbtoy101rymjcclhgy` FOREIGN KEY (`updated_by_admin_id`) REFERENCES `admins` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `cart_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `added_at` datetime(6) DEFAULT NULL,
  `option_snapshot_json` json DEFAULT NULL,
  `quantity` int NOT NULL,
  `unit_price` decimal(13,2) NOT NULL,
  `cart_id` bigint NOT NULL,
  `sku_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKpcttvuq4mxppo8sxggjtn5i2c` (`cart_id`),
  KEY `FKaupmmpkdyywh0qpwp3rjwdf2d` (`sku_id`),
  CONSTRAINT `FKaupmmpkdyywh0qpwp3rjwdf2d` FOREIGN KEY (`sku_id`) REFERENCES `skus` (`id`),
  CONSTRAINT `FKpcttvuq4mxppo8sxggjtn5i2c` FOREIGN KEY (`cart_id`) REFERENCES `carts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `carts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `currency` varchar(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK64t7ox312pqal3p7fg9o503c2` (`user_id`),
  CONSTRAINT `FKb5o626f86h46m4s7ms6ginnop` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `inquiries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `inquiry_code` varchar(40) NOT NULL,
  `user_id` bigint NOT NULL,
  `order_id` bigint DEFAULT NULL,
  `category` varchar(30) NOT NULL,
  `title` varchar(200) NOT NULL,
  `content` text NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `is_secret` bit(1) NOT NULL DEFAULT b'0',
  `answered_by_id` bigint DEFAULT NULL,
  `answered_at` datetime(6) DEFAULT NULL,
  `answer_content` text,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inquiries_code` (`inquiry_code`),
  KEY `fk_inquiries_order` (`order_id`),
  KEY `fk_inquiries_admin` (`answered_by_id`),
  KEY `idx_inquiries_user_id` (`user_id`),
  KEY `idx_inquiries_status` (`status`),
  KEY `idx_inquiries_created_at` (`created_at`),
  CONSTRAINT `fk_inquiries_admin` FOREIGN KEY (`answered_by_id`) REFERENCES `admins` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_inquiries_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_inquiries_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `notices` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `notice_code` varchar(40) NOT NULL,
  `title` varchar(200) NOT NULL,
  `content` text NOT NULL,
  `is_pinned` bit(1) NOT NULL DEFAULT b'0',
  `is_active` bit(1) NOT NULL DEFAULT b'1',
  `created_by_admin_id` bigint DEFAULT NULL,
  `updated_by_admin_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notices_notice_code` (`notice_code`),
  KEY `fk_notices_created_by` (`created_by_admin_id`),
  KEY `fk_notices_updated_by` (`updated_by_admin_id`),
  CONSTRAINT `fk_notices_created_by` FOREIGN KEY (`created_by_admin_id`) REFERENCES `admins` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_notices_updated_by` FOREIGN KEY (`updated_by_admin_id`) REFERENCES `admins` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `order_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `artist_code_snapshot` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `artist_name_snapshot` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `discount_amount` decimal(13,2) NOT NULL,
  `line_total_amount` decimal(13,2) NOT NULL,
  `quantity` int NOT NULL,
  `review_written` bit(1) NOT NULL,
  `sku_code_snapshot` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `sku_name_snapshot` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `sku_snapshot_json` json DEFAULT NULL,
  `tax_amount` decimal(13,2) NOT NULL,
  `unit_price` decimal(13,2) NOT NULL,
  `artist_id` bigint DEFAULT NULL,
  `order_id` bigint NOT NULL,
  `sku_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKd1mdd1ixwwdrps2ip6c8o36wc` (`artist_id`),
  KEY `FKbioxgbv59vetrxe0ejfubep1w` (`order_id`),
  KEY `FKdxnwqglwb57kr44psxxwj44wf` (`sku_id`),
  CONSTRAINT `FKbioxgbv59vetrxe0ejfubep1w` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `FKd1mdd1ixwwdrps2ip6c8o36wc` FOREIGN KEY (`artist_id`) REFERENCES `artists` (`id`),
  CONSTRAINT `FKdxnwqglwb57kr44psxxwj44wf` FOREIGN KEY (`sku_id`) REFERENCES `skus` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `order_shipments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `address1` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL,
  `address2` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `carrier_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `delivered_at` datetime(6) DEFAULT NULL,
  `delivery_request` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `recipient_name` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `recipient_phone` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `shipped_at` datetime(6) DEFAULT NULL,
  `tracking_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `zip_code` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `order_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKhd8lnx4jbisrq60xdfnbs4xhi` (`order_id`),
  CONSTRAINT `FKjfylqpr3hdbcse0lpg3q5hfh` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `currency` varchar(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `discount_amount` decimal(13,2) NOT NULL,
  `order_no` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `order_status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `orderer_email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `orderer_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `orderer_phone` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `payment_status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `product_amount` decimal(13,2) NOT NULL,
  `shipping_amount` decimal(13,2) NOT NULL,
  `tax_amount` decimal(13,2) NOT NULL,
  `total_amount` decimal(13,2) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKg8pohnngqi5x1nask7nff2u7w` (`order_no`),
  KEY `FK32ql8ubntj5uh44ph9659tiih` (`user_id`),
  CONSTRAINT `FK32ql8ubntj5uh44ph9659tiih` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `password_reset_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `expired_at` datetime(6) NOT NULL,
  `is_used` bit(1) NOT NULL,
  `is_verified` bit(1) NOT NULL,
  `token` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `payment_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(13,2) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `event_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload_json` json DEFAULT NULL,
  `provider_event_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payment_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKe86hr8sww5wcn9aiilg8ykchi` (`payment_id`),
  CONSTRAINT `FKe86hr8sww5wcn9aiilg8ykchi` FOREIGN KEY (`payment_id`) REFERENCES `payments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `payments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `approval_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approved_amount` decimal(13,2) NOT NULL,
  `approved_at` datetime(6) DEFAULT NULL,
  `cancelled_amount` decimal(13,2) NOT NULL,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `currency` varchar(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `failed_at` datetime(6) DEFAULT NULL,
  `failure_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `failure_message` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `method` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `payment_no` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `pg_transaction_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `provider` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `raw_response_json` json DEFAULT NULL,
  `requested_amount` decimal(13,2) NOT NULL,
  `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `order_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKg30exdhkq4uo06wsjfqkxm790` (`payment_no`),
  KEY `FK81gagumt0r8y3rmudcgpbk42l` (`order_id`),
  CONSTRAINT `FK81gagumt0r8y3rmudcgpbk42l` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `return_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `admin_memo` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `processed_at` datetime(6) DEFAULT NULL,
  `reason` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason_detail` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `refund_amount` decimal(13,2) DEFAULT NULL,
  `return_no` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `return_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `order_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKc188oeek5pd8eoa3na8iij8ng` (`return_no`),
  KEY `FKbski88d6kewx0cbj5pk7nes01` (`order_id`),
  KEY `FK6pd9hi2rbbct43io2pgcma1sh` (`user_id`),
  CONSTRAINT `FK6pd9hi2rbbct43io2pgcma1sh` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKbski88d6kewx0cbj5pk7nes01` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `sku_categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type` varchar(10) NOT NULL COMMENT 'MAIN(대분류) / SUB(소분류)',
  `code` varchar(50) NOT NULL COMMENT '상품에 저장되는 값',
  `name` varchar(50) NOT NULL COMMENT '화면 표시명',
  `sort_order` int NOT NULL DEFAULT '0',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_categories_type_code` (`type`,`code`),
  KEY `idx_sku_categories_type_active` (`type`,`is_active`,`sort_order`),
  CONSTRAINT `ck_sku_categories_type` CHECK ((`type` in (_utf8mb4'MAIN',_utf8mb4'SUB')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `sku_media` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `alt_text` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `angle_degree` decimal(6,2) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `file_url` varchar(700) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_primary` bit(1) NOT NULL,
  `media_role` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `media_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `meta_json` json DEFAULT NULL,
  `sort_order` int NOT NULL,
  `thumbnail_url` varchar(700) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sku_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9hfdg0nbgcdm9g7ua9fqhxd75` (`sku_id`),
  CONSTRAINT `FK9hfdg0nbgcdm9g7ua9fqhxd75` FOREIGN KEY (`sku_id`) REFERENCES `skus` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `sku_review_media` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `file_url` varchar(700) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `media_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL,
  `thumbnail_url` varchar(700) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `review_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKjgvcya1jqy8keba68hqwbq7f` (`review_id`),
  CONSTRAINT `FKjgvcya1jqy8keba68hqwbq7f` FOREIGN KEY (`review_id`) REFERENCES `sku_reviews` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `sku_review_stats` (
  `sku_id` bigint NOT NULL,
  `avg_rating` decimal(3,2) NOT NULL,
  `rating_sum` bigint NOT NULL,
  `review_count` int NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`sku_id`),
  CONSTRAINT `FKsp864rk6o7y763ogcq8cy653q` FOREIGN KEY (`sku_id`) REFERENCES `skus` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `sku_reviews` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `admin_memo` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content` tinytext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `is_featured` bit(1) NOT NULL,
  `is_visible` bit(1) NOT NULL,
  `like_count` int NOT NULL,
  `moderated_at` datetime(6) DEFAULT NULL,
  `rating` int NOT NULL,
  `report_count` int NOT NULL,
  `review_code` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `review_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `moderated_by_admin_id` bigint DEFAULT NULL,
  `order_id` bigint NOT NULL,
  `order_item_id` bigint NOT NULL,
  `sku_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKieqv2fu65su10uk3qmf82n0v4` (`review_code`),
  UNIQUE KEY `UKtqbn04jut26sqioc6x72f2pao` (`order_item_id`),
  KEY `FKr74u7mk2olmc8bjt6ll2hxksy` (`moderated_by_admin_id`),
  KEY `FK7rp0pe6ni2uqkoaj7mua8whbe` (`order_id`),
  KEY `FKptgykknp1yma3hxgq2e3fm7ag` (`sku_id`),
  KEY `FKs248uqe5pxffv3e6wxqpppsyf` (`user_id`),
  CONSTRAINT `FK7rp0pe6ni2uqkoaj7mua8whbe` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `FKcbtqa26dkruo4iaqp2e0sp2q8` FOREIGN KEY (`order_item_id`) REFERENCES `order_items` (`id`),
  CONSTRAINT `FKptgykknp1yma3hxgq2e3fm7ag` FOREIGN KEY (`sku_id`) REFERENCES `skus` (`id`),
  CONSTRAINT `FKr74u7mk2olmc8bjt6ll2hxksy` FOREIGN KEY (`moderated_by_admin_id`) REFERENCES `admins` (`id`),
  CONSTRAINT `FKs248uqe5pxffv3e6wxqpppsyf` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `sku_stock_ledger` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `delta` int NOT NULL,
  `memo` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reason` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `ref_id` bigint DEFAULT NULL,
  `ref_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sku_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKmry13618eb8rx6d2l15gcsmxy` (`sku_id`),
  KEY `idx_sku_stock_ledger_sku_id` (`sku_id`,`delta`),
  CONSTRAINT `FKmry13618eb8rx6d2l15gcsmxy` FOREIGN KEY (`sku_id`) REFERENCES `skus` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `skus` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `ar_asset_url` varchar(700) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ar_preview_image_url` varchar(700) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `currency` varchar(3) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `depth_cm` decimal(38,2) DEFAULT NULL,
  `description` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `edition_number` int DEFAULT NULL,
  `edition_size` int DEFAULT NULL,
  `genre` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `height_cm` decimal(38,2) DEFAULT NULL,
  `is_limited_edition` bit(1) NOT NULL,
  `list_price` decimal(13,2) NOT NULL,
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `primary_image_url` varchar(700) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `published_at` datetime(6) DEFAULT NULL,
  `sale_price` decimal(13,2) DEFAULT NULL,
  `sku_code` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `sku_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `slug` varchar(220) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `spine_pictures_json` json DEFAULT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `weight_kg` decimal(38,2) DEFAULT NULL,
  `width_cm` decimal(38,2) DEFAULT NULL,
  `artist_id` bigint NOT NULL,
  `badges` json DEFAULT NULL,
  `material` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '재질/소재',
  `material_description` longtext COLLATE utf8mb4_unicode_ci COMMENT '재질/소재 상세 설명',
  `packaging_title` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '포장 섹션 제목',
  `packaging_description` longtext COLLATE utf8mb4_unicode_ci COMMENT '포장 섹션 설명',
  `main_category` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NORMAL' COMMENT '대분류 코드 — sku_categories.code (type=MAIN)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK3rme7uhv9f295cmair1aiosjs` (`sku_code`),
  UNIQUE KEY `UK252gcslknmvuiyxqw64g82i7n` (`slug`),
  KEY `FK5nwgm26jl51iu2nxqd4m7djb9` (`artist_id`),
  KEY `idx_skus_main_category` (`main_category`),
  CONSTRAINT `FK5nwgm26jl51iu2nxqd4m7djb9` FOREIGN KEY (`artist_id`) REFERENCES `artists` (`id`),
  CONSTRAINT `ck_skus_edition` CHECK ((((`edition_size` is null) or (`edition_size` > 0)) and ((`edition_number` is null) or ((`edition_size` is not null) and (`edition_number` > 0) and (`edition_number` <= `edition_size`)))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `user_addresses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `address1` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL,
  `address2` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_default` bit(1) NOT NULL,
  `label` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `recipient_name` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `recipient_phone` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `zip_code` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKn2fisxyyu3l9wlch3ve2nocgp` (`user_id`),
  CONSTRAINT `FKn2fisxyyu3l9wlch3ve2nocgp` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `last_login_at` datetime(6) DEFAULT NULL,
  `name` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `oauth_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `oauth_provider` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_code` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `fcm_token` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `toss_disconnected_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UKt4oh2dnaf9b4jc7qj8rxswgyh` (`user_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `wishlist_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `sku_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKjpj4117evfras78dau2vsp2sw` (`sku_id`),
  KEY `FKmmj2k1i459yu449k3h1vx5abp` (`user_id`),
  CONSTRAINT `FKjpj4117evfras78dau2vsp2sw` FOREIGN KEY (`sku_id`) REFERENCES `skus` (`id`),
  CONSTRAINT `FKmmj2k1i459yu449k3h1vx5abp` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


