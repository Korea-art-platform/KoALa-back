# 헬스체크 — 의존성 등급

무엇이 죽으면 "서비스가 죽은 것"인지를 의존성별로 나눈 기준이다.
`/actuator/health` 하나로 판정하던 것을 liveness / readiness / 참고로 갈랐다.

## 등급표

| 의존성 | 등급 | 근거 | 없을 때 실제 동작 |
|---|---|---|---|
| MySQL | **치명** | 모든 읽기·쓰기의 원천. 폴백 없음 | 전 API 실패 |
| Redis | **치명** | 세 용도 중 액세스 토큰 블랙리스트에 폴백이 없다 | `TokenBlacklistService.isBlacklisted` 가 fail-secure 로 `true` 를 반환 → `JwtFilter` 가 인증 컨텍스트를 세우지 않음 → **로그인 사용자 전원 401** |
| 디스크 여유 | **치명** | 로그·업로드 임시파일 기록 불가 | 요청 처리 실패 |
| 메일(SMTP) | 저하 | 주문 확인 메일 전용 | 메일만 못 감. 주문은 정상 저장되고, 발송 실패는 슬랙 알림으로 잡힌다 |
| 나이스(PG) | 참고 | **외부 시스템.** 내 인스턴스 건강과 무관 | 결제 불가. 인스턴스를 트래픽에서 빼면 다른 기능까지 같이 죽으므로 판정에 넣지 않는다 |
| S3 | 관측 안 함 | 확인하려면 `s3:ListBucket` 이 필요한데, 코드는 `putObject` 만 쓴다 | 어드민 업로드 실패. 이미 배포된 이미지는 CDN 으로 계속 조회된다. 업로드 실패는 500 알림을 탄다 |
| Kafka | 해당 없음 | `koala.events.kafka.enabled=false`, 브로커 없음 | — |

### Redis 등급을 치명으로 둔 이유

세 용도의 폴백 상황이 서로 다르다.

| 용도 | 클래스 | Redis 장애 시 |
|---|---|---|
| 재고 캐시 | `StockCacheService` | 예외를 잡고 DB 로 조회 — 폴백 있음 |
| 레이트리밋 | `RateLimitFilter` | 예외 시 요청 허용 — 폴백 있음 |
| 액세스 토큰 블랙리스트 | `TokenBlacklistService` | **`return true`** — 폴백 없음 |

블랙리스트만 폴백이 없고, 그 하나가 인증 전체를 막는다. 폴백이 있는 둘은 의미가 없다.

이것을 "저하"로 낮추려면 블랙리스트를 fail-open 으로 바꿔야 하는데, 그건 보안 정책
변경이지 관측 작업이 아니다. **관측을 위해 도메인 판단을 바꾸지 않는다.** 지금 동작
그대로를 등급에 반영했다.

## 배치 원칙

- **liveness 에 외부 의존성을 넣지 않는다.** 넣으면 DB 장애 때 프로세스가 재시작
  루프에 빠진다. 재시작해도 DB 는 돌아오지 않는다.
- **외부 결제사를 readiness 에 넣지 않는다.** 외부 장애로 내 인스턴스가 트래픽에서
  빠지면 결제 말고 다른 기능까지 같이 멈춘다.
- **커스텀 지표에는 반드시 타임아웃을 건다.** 헬스체크가 느려지는 것 자체가 장애다.
  `NicePayHealthIndicator` 는 별도 스레드에서 2초 상한으로 돌고, 넘으면 그 지표만
  UNKNOWN 이 된다.

## 그룹 구성

```
liveness   : livenessState
readiness  : readinessState, db, redis, diskSpace
```

`nicepay` 와 `mail` 은 어느 그룹에도 넣지 않는다. 종합(`/actuator/health`)에만 나온다.

**이 설정은 yml 이 아니라 `HealthGroupDefaults` 에 있다.** `application*.yml` 이
`.gitignore` 에 걸려 있어 CI 가 만드는 jar 에는 설정 파일이 들어가지 않기 때문이다.
yml 에 적으면 운영 서버의 사본에도 같은 내용을 손으로 넣어야 하고, 둘이 갈리면
테스트는 통과하는데 운영만 다른 판정을 하게 된다.

`EnvironmentPostProcessor` 로 가장 낮은 우선순위에 넣으므로, 서버 yml 에 값을 적으면
그쪽이 이긴다. 기본값이 운영 설정을 덮지 않는다.

## 배포 판정

`.github/workflows/deploy.yml` 은 기동 확인과 롤백 확인 모두
`/actuator/health/readiness` 를 본다. 종합이 아니다.

| 상황 | 전 | 후 |
|---|---|---|
| 메일(SMTP) 장애 중 배포 | 롤백됨 | 통과 |
| Redis 장애 중 배포 | 롤백됨 | 롤백됨 (의도한 대로) |
| DB 장애 시 readiness | UP (아무것도 안 봤다) | DOWN |
| 나이스 장애 시 | 영향 없음 | 영향 없음, 상태만 표시 |

가장 큰 변화는 **메일이 배포를 막지 않는다**와 **readiness 가 DB 를 본다**이다.
전에는 readiness 그룹이 `readinessState` 하나뿐이라 아무 의존성도 보지 않았고,
정작 배포 판정은 등록된 모든 지표를 합친 종합 상태로 하고 있었다.

## 상세 노출

`show-details: when-authorized` 다. 경로는 `permitAll` 이지만 익명 요청에는
`{"status":"UP"}` 만 나가고 기여자별 상세는 인증된 요청에만 보인다.
어드민 엔드포인트를 등록 핸들러 순회로 전수 확인하는 `AdminEndpointSecurityTest` 와
어긋나지 않는다 — actuator 는 `/admin/api` 경로가 아니므로 그 검사 대상이 아니고,
상세 차단은 시큐리티가 아니라 actuator 자체 설정으로 한다.

외부에서는 접근되지 않는다. CloudFront 가 `/actuator/**` 를 오리진으로 넘기지 않아
바깥에서 부르면 SPA HTML 이 돌아온다.

## 테스트

| 파일 | 확인하는 것 |
|---|---|
| `ReadinessCriticalTest` | Redis DOWN → readiness DOWN, liveness 는 UP 유지 |
| `ReadinessGroupTest` | 메일 DOWN → readiness UP, 종합은 DOWN. 그룹 구성 |
| `HealthGroupDefaultsTest` | 설정이 없을 때 기본값을 채우는지, 서버 설정이 있으면 그쪽이 이기는지 |
| `NicePayHealthIndicatorTest` | 닿지 않아도 DOWN 이 아님, 타임아웃 상한 |

`ReadinessCriticalTest` 는 실제 Redis 컨테이너를 죽이는 대신 자동 등록 지표를 끄고
DOWN 을 반환하는 지표를 같은 이름으로 넣는다. 컨테이너가 테스트 클래스 사이에
공유되어 실제로 멈추면 다른 테스트가 깨지기 때문이다. 확인하려는 것은 "Redis 가
DOWN 일 때 readiness 가 어떻게 되는가"이고, Redis 접속 실패를 DOWN 으로 판정하는
부분은 스프링이 제공하는 지표가 담당한다.
