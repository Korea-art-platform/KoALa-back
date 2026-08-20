# KoALa Backend

아트토이·작품 커머스 플랫폼 KoALa 의 백엔드 API 서버입니다.

- 운영: https://koala-art.co.kr
- 프론트엔드 저장소: `Koalaweb`
- 모바일(Capacitor) 저장소: `KoALa-mobile`

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| 언어/런타임 | Java 21 |
| 프레임워크 | Spring Boot 4.0.3 (Web, Data JPA, Security, Validation, Actuator) |
| DB | MySQL 8.0 + Flyway 마이그레이션 |
| 캐시 | Redis (재고 캐시, 액세스 토큰 블랙리스트, 레이트리밋) |
| 인증 | JWT + OAuth2 (Kakao, Naver) · 리프레시 토큰은 MySQL 저장 |
| 결제 | PG 추상화 (`PaymentProvider`) — 나이스페이먼츠(운영) · 토스 · 페이플 |
| 스토리지 | AWS S3 + CloudFront |
| 메시징 | Kafka (선택 — 기본 비활성) |
| 테스트 | JUnit 5, Mockito, Testcontainers, EmbeddedKafka |

## 로컬 실행

### 1. 인프라 기동

```bash
docker compose up -d
```

MySQL(3306), Redis(6379), Kafka(9092), Kafka UI(8090)가 뜹니다.
`docker-compose.yml` 이 `.env` 의 `MYSQL_*` 값을 참조하므로 먼저 준비해야 합니다.

### 2. 시크릿 설정

프로젝트 루트에 `.env.properties` 를 만들고 아래 키를 채웁니다.
(`application.yml` 의 `spring.config.import` 가 자동으로 읽습니다. 없으면 기동은 되지만 OAuth·결제가 동작하지 않습니다.)

```properties
DB_USERNAME=
DB_PASSWORD=
JWT_SECRET=
NAVER_CLIENT_ID=
NAVER_CLIENT_SECRET=
KAKAO_CLIENT_ID=
KAKAO_CLIENT_SECRET=

# 결제 — 쓰는 PG 하나만 켠다 (나머지는 enabled=false 면 빈이 뜨지 않음)
NICEPAY_ENABLED=true
NICEPAY_API_BASE=https://sandbox-api.nicepay.co.kr/v1
NICEPAY_CLIENT_KEY=
NICEPAY_SECRET_KEY=
# TOSS_SECRET_KEY= / PAYPLE_* 는 해당 PG 로 전환할 때만
```

전체 키 목록과 설명은 `.env.properties.example` 을 참고하세요. 프론트도 `VITE_PG` 값을
같은 PG 로 맞춰야 결제창이 바뀝니다(프론트는 빌드 시점에 값이 박힘).

### 3. 애플리케이션 기동

```bash
./gradlew bootRun
```

기본 프로필은 `local` 이며 8080 포트로 뜹니다.
로컬 프로필은 S3 대신 `./uploads` 디렉터리에 파일을 저장하고 `/uploads/**` 로 서빙합니다.

## 테스트

```bash
./gradlew test
```

통합 테스트가 **Testcontainers 로 MySQL·Redis 컨테이너를 띄우므로 Docker 가 필요합니다.**
컨테이너는 `IntegrationTestSupport` 에서 싱글턴으로 관리되어 테스트 클래스 간 재사용됩니다.

> Docker Engine 29 이상은 API 1.44 미만 요청을 거부합니다. `build.gradle` 의 test 태스크에서
> `api.version` 시스템 프로퍼티와 Windows용 `DOCKER_HOST` 를 지정해 두었습니다.

## 아키텍처 — 알아둘 것

### 재고는 컬럼이 아니라 원장(ledger)입니다

`skus` 에 재고 컬럼이 없습니다. `sku_stock_ledger` 에 증감(delta) 행을 쌓고 `SUM` 으로 현재고를 구합니다.
이력이 그대로 남고 동시성에 강한 대신, 조회 비용이 커서 두 가지 장치가 있습니다.

- Redis 캐시 (`stock:{skuId}`, TTL 10분) — 목록 조회는 MGET 으로 한 번에 읽습니다
- `idx_sku_stock_ledger_sku_id (sku_id, delta)` 커버링 인덱스 (V20)

### 재고 변경은 비관적 락으로 직렬화합니다

`StockService` 의 차감·복원·관리자 조정은 모두 SKU row 에 `SELECT ... FOR UPDATE` 를 겁니다.

**여러 SKU 를 다룰 때는 반드시 `skuId` 오름차순으로 락을 잡아야 합니다.** 순서가 다르면
서로의 락을 기다리는 데드락이 납니다. 주문 생성(`OrderService.sortByLockOrder`), 주문 취소,
만료 처리, 반품 승인이 모두 이 규칙을 따릅니다.

복원 경로는 `refresh(PESSIMISTIC_WRITE)` 를 씁니다. 호출자가 이미 같은 트랜잭션에서 Sku 를
읽어둔 경우 `findByIdForUpdate` 만으로는 엔티티 필드가 갱신되지 않아, 품절 상태 판단이 틀어지기 때문입니다.

### 결제는 트랜잭션을 3단계로 쪼갭니다

외부 PG 호출이 트랜잭션 안에 있으면 응답을 기다리는 내내 DB 커넥션이 묶입니다.

```
① beginConfirm      [트랜잭션] 검증 + IN_PROGRESS 선점 → 커밋
② provider.confirm  [트랜잭션 밖] PG HTTP 호출 (connect 3s / read 10s)
③ apply*            [트랜잭션] 결과 반영 → 커밋
```

DB 단계는 `PaymentTransactionService` 에 모여 있습니다. 같은 클래스 안에서 호출하면
Spring 프록시를 타지 않아 `@Transactional` 이 무시되므로(자기호출) 별도 빈으로 분리한 것입니다.
`OrderTransactionService`, `ReturnRequestTransactionService` 도 같은 이유로 존재합니다.

**PG 응답을 못 받은 경우를 실패로 단정하지 않습니다.** 타임아웃·5xx 는 `UNKNOWN` 으로 분류하고
재조회(`lookup`)로 확정을 시도한 뒤, 그래도 모르면 `IN_DOUBT` 로 남깁니다. 이 상태의 결제는
재승인이 차단되고, 만료 스케줄러가 해당 주문을 건너뛰며, 웹훅이 오면 확정됩니다.
어드민 대시보드의 "확인 필요 결제" 블록에 노출됩니다.

### 주문 후처리는 이벤트로 분리합니다

결제 승인이 커밋된 뒤 `@TransactionalEventListener(AFTER_COMMIT)` 가 후처리를 트리거합니다.
커밋 전에 발행하면 롤백 시 유령 이벤트(결제되지 않은 주문의 완료 메일)가 나갑니다.

`koala.events.kafka.enabled=true` 면 `order.completed` 토픽으로 발행하고 컨슈머가 메일을 보냅니다.
**기본값은 false** 이며, 이때는 릴레이가 커밋 후 직접 메일을 보냅니다(운영에 브로커가 없기 때문).

### 헬스체크는 의존성 등급으로 나뉩니다

`/actuator/health` 하나로 판정하던 것을 **치명 · 저하 · 참고**로 갈랐습니다.

- **readiness** (`/actuator/health/readiness`) — 치명 의존성만: DB · Redis... 는 아니고
  `readinessState` · `db` · `diskSpace`. Redis 는 세 용도 모두 폴백이 있어 **저하**로 내려
  readiness 에서 빠져 있습니다(죽어도 트래픽은 받음).
- **liveness** — 외부 의존성 없음. 넣으면 DB 장애 때 재시작 루프에 빠집니다.
- 메일·결제사(나이스)는 어느 그룹에도 없고 종합 상태에만 표시(참고 등급).

그룹 구성은 `application.yml` 이 아니라 `HealthGroupDefaults`(`EnvironmentPostProcessor`)에
있습니다 — `application*.yml` 이 `.gitignore` 라 jar 에 안 실리기 때문입니다.
자세한 근거는 `docs/health-check.md` 참고.

### 업로드 이미지는 저장 전에 줄입니다

`ImageOptimizer` 가 긴 변 1600px 초과 시 축소하고 JPEG 을 품질 0.82 로 재인코딩합니다.
포맷·확장자는 유지하며, 최적화에 실패하거나 결과가 더 커지면 원본을 그대로 올립니다.
`koala.image.max-dimension`, `koala.image.jpeg-quality` 로 조정할 수 있습니다.

## 마이그레이션 주의사항

운영·개발 DB 는 **V14 를 baseline 으로 시작**했습니다. 즉 V1~V14 는 Flyway 가 실제로 적용한 적이 없고,
스키마는 다른 경로로 만들어졌습니다.

그 결과 **빈 DB 에 V1 부터 재생하면 실패합니다** (V13/V14 의 FK 컬럼 타입 불일치).
Testcontainers 통합 테스트가 운영 마이그레이션 대신 `classpath:db/noop` + Hibernate `create-drop` 을
쓰는 이유가 이것입니다.

새 마이그레이션을 추가할 때는 최신 번호(현재 V24) 이후를 쓰고, 기존 파일은 수정하지 마세요
(적용된 마이그레이션의 체크섬이 바뀌면 `validate-on-migrate: true` 때문에 기동이 실패합니다).

## 배포

`main` 브랜치 푸시 시 GitHub Actions(`.github/workflows/deploy.yml`)가 자동 실행됩니다.

```
bootJar -x test → S3 경유 EC2 전송 → systemd(koala) 재시작
→ /actuator/health/readiness 확인 → 실패 시 이전 jar 로 자동 롤백
```

기동 판정은 종합 상태가 아니라 **readiness** 를 봅니다. 메일(SMTP)이 느리기만 해도
멀쩡한 새 버전이 롤백되던 문제를 막기 위해서입니다. 기동에 실패하면 직전 jar 로
자동 복구하고 워크플로는 실패로 끝납니다(슬랙 알림).

테스트는 CI 에서 실행되지 않으므로 **푸시 전에 로컬에서 `./gradlew test` 를 돌려주세요.**

운영 환경변수는 EC2 의 `/opt/koala/.env` 에 있습니다
(systemd `EnvironmentFiles` 로 주입. `/home/ubuntu/.env` 는 사용하지 않는 잔재 파일입니다).

배포 후 확인:

```bash
journalctl -u koala -f
```

## 프로젝트 구조

```
src/main/java/com/koala/koalaback/
├── api/            컨트롤러 (public / admin 분리)
├── domain/         도메인별 entity·repository·service·dto
│   ├── order/      주문 + 이벤트(event 패키지)
│   ├── payment/    결제 + PG 프로바이더
│   ├── sku/        상품·재고
│   └── ...
├── global/         공통 설정·예외·응답 포맷·유틸
└── infra/          외부 연동 (S3, Redis, 메일, FCM)
```
