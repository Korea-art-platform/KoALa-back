-- ============================================================
-- V11: 토스 로그인(앱인토스) 지원을 위한 oauth_provider 확장
-- ============================================================

-- oauthProvider 컬럼에 TOSS 값 추가 (VARCHAR(20) 그대로 사용, 별도 스키마 변경 불필요)
-- 기존: KAKAO, NAVER
-- 추가: TOSS

-- toss_id 컬럼 추가 (앱인토스 userKey 저장 — provider별 고유 식별자)
-- 기존 oauthId 컬럼을 TOSS 용도로도 사용하므로 별도 컬럼 추가는 생략
-- 단, oauthId 컬럼 길이가 100자로 충분한지 확인 (앱인토스 userKey는 UUID 수준)

-- 앱인토스 연결 해제 웹훅 수신용 필드 (토스 앱에서 연결 끊기 시)
-- 주의: MySQL 은 ADD COLUMN IF NOT EXISTS 를 지원하지 않는다(MariaDB 전용 문법).
-- 빈 DB 에 V1 부터 재생할 때 문법 오류로 실패하므로 표준 구문을 쓴다.
ALTER TABLE users
    ADD COLUMN toss_disconnected_at DATETIME NULL
    COMMENT '앱인토스 연결 해제 시각';
