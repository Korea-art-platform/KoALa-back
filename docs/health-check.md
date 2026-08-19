# 헬스체크 — 의존성 등급

무엇이 죽으면 "서비스가 죽은 것"인지를 의존성별로 나눈 기준이다.
`/actuator/health` 하나로 판정하던 것을 liveness / readiness / 참고로 갈랐다.

## 등급표

| 의존성 | 등급 | 근거 | 없을 때 실제 동작 |
|---|---|---|---|
| MySQL | **치명** | 모든 읽기·쓰기의 원천. 폴백 없음 | 전 API 실패 |
| Redis | 저하 | 세 용도 모두 폴백이 있다 (2026-08-19 블랙리스트 폴백 추가) | 재고는 DB 조회, 레이트리밋은 통과 허용, 블랙리스트는 미적용 + 슬랙 알림. 서비스는 계속된다 |
| 디스크 여유 | **치명** | 로그·업로드 임시파일 기록 불가 | 요청 처리 실패 |
| 메일(SMTP) | 저하 | 주문 확인 메일 전용 | 메일만 못 감. 주문은 정상 저장되고, 발송 실패는 슬랙 알림으로 잡힌다 |
| 나이스(PG) | 참고 | **외부 시스템.** 내 인스턴스 건강과 무관 | 결제 불가. 인스턴스를 트래픽에서 빼면 다른 기능까지 같이 죽으므로 판정에 넣지 않는다 |
| S3 | 관측 안 함 | 확인하려면 `s3:ListBucket` 이 필요한데, 코드는 `putObject` 만 쓴다 | 어드민 업로드 실패. 이미 배포된 이미지는 CDN 으로 계속 조회된다. 업로드 실패는 500 알림을 탄다 |
| Kafka | 해당 없음 | `koala.events.kafka.enabled=false`, 브로커 없음 | — |

### Redis 등급이 치명에서 저하로 내려온 경위

처음 등급을 매길 때는 **치명**이었다. 세 용도 중 블랙리스트만 폴백이 없었기 때문이다.

| 용도 | 클래스 | Redis 장애 시 |
|---|---|---|
| 재고 캐시 | `StockCacheService` | 예외를 잡고 DB 로 조회 |
| 레이트리밋 | `RateLimitFilter` | 예외 시 요청 허용 |
| 액세스 토큰 블랙리스트 | `TokenBlacklistService` | 2026-08-18 까지 **`return true`** (fail-secure) |

fail-secure 는 `JwtFilter` 가 인증 컨텍스트를 세우지 않게 만들어, Redis 장애 동안
**로그인 사용자 전원이 401** 을 받았다. 그런데 그렇게 해서 지키는 것은
"이미 유출된 토큰이 로그아웃 후 남은 수명 동안 쓰이는 것"뿐이다. 액세스 토큰 수명이
30분이고 리프레시 토큰은 로그아웃 시 MySQL 에서 지워지므로 새 토큰은 발급되지 않는다.

**최대 30분짜리 좁은 창을 지키려고 서비스 전체를 끄는 교환**이라 뒤집었다.
2026-08-19 부터 조회 실패 시 통과시키고 슬랙으로 알린다. 방어가 꺼진 것을 사람이
알 수 있어야 하므로 조용히 넘어가지 않는다.

그 결과 세 용도 모두 폴백이 생겨 Redis 는 저하가 됐고, readiness 에서 빠졌다.
**Redis 가 죽어도 주문은 받는다.**

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
readiness  : readinessState, db, diskSpace
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
| Redis 장애 중 배포 | 롤백됨 | 통과 (폴백이 생겨 저하로 내려감) |
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
| `ReadinessCriticalTest` | DB DOWN → readiness DOWN, liveness 는 UP 유지 |
| `ReadinessGroupTest` | 메일 DOWN → readiness UP, 종합은 DOWN. 그룹 구성 |
| `TokenBlacklistFallbackTest` | Redis 조회 실패 시 통과 + 알림, 정상일 때는 그대로 차단 |
| `HealthGroupDefaultsTest` | 설정이 없을 때 기본값을 채우는지, 서버 설정이 있으면 그쪽이 이기는지 |
| `NicePayHealthIndicatorTest` | 닿지 않아도 DOWN 이 아님, 타임아웃 상한 |

`ReadinessCriticalTest` 는 실제 컨테이너를 죽이는 대신 자동 등록 지표를 끄고 DOWN 을
반환하는 지표를 같은 이름으로 넣는다. 컨테이너가 테스트 클래스 사이에 공유되어 실제로
멈추면 다른 테스트가 깨지기 때문이다. 확인하려는 것은 "치명 지표가 DOWN 일 때
readiness 가 어떻게 되는가"이고, 접속 실패를 DOWN 으로 판정하는 부분은 스프링이
제공하는 지표가 담당한다.

## Redis 타임아웃

`RedisTimeoutDefaults` 가 명령·접속 타임아웃을 **300ms** 로 잡는다. 그룹 설정과 같은
이유로 yml 이 아니라 코드에 둔다.

전에는 3초였다. Redis 가 응답 없이 죽으면 요청 하나당 레이트리밋 3초 + 블랙리스트
3초를 기다렸고, 스레드가 묶여 Redis 장애가 곧 전체 정지가 됐다. 폴백이 있어도
**폴백에 도달하기까지가 느리면 소용이 없다.** 300ms 면 같은 상황에서 0.6초로 끝난다.
