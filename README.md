# msa-gateway-practice

MSA **API Gateway 패턴** 학습 프로젝트 (Phase 1). Spring Cloud Gateway + Eureka 기반.

## 아키텍처

```
[클라이언트]
    │  (8000번 포트만 호출)
    ▼
[API Gateway :8000]  ── 로깅 필터 → JWT 인증 필터 → 라우팅(lb://)
    ├─ /order/**  → ORDER-SERVICE
    └─ /member/** → MEMBER-SERVICE
         ▲
   [Eureka :8761] ← order-service / member-service 가 자기 주소 등록
```

## 모듈

| 모듈 | 포트 | 역할 |
|------|------|------|
| eureka-server | 8761 | 서비스 레지스트리(디스커버리) |
| api-gateway | 8000 | 단일 진입점: 라우팅·로드밸런싱·로깅·JWT 인증 |
| order-service | 랜덤 | 더미 주문 서비스 (LB 확인용 포트 응답) |
| member-service | 랜덤 | 로그인 → JWT 발급 |

## 구현된 기능 (Phase 1)

1. **라우팅 + 로드밸런싱** — Eureka에 등록된 서비스로 `lb://` 라우팅
2. **글로벌 로깅 필터** — 모든 요청에 요청ID 부여, 요청/응답·소요시간 로깅
3. **JWT 인증 필터** — Bearer 토큰 검증, 실패 시 401 차단, 성공 시 `X-User-Id` 전달
   (화이트리스트: `/member/login`)

## 기술 스택

Java 17 · Gradle(멀티모듈) · Spring Boot 3.3.4 · Spring Cloud 2023.0.3 ·
Spring Cloud Gateway(WebFlux) · Netflix Eureka · jjwt 0.12.6

## 실행 순서

각각 별도 터미널에서:
```bash
./gradlew :eureka-server:bootRun     # 1. 레지스트리 (먼저)
./gradlew :order-service:bootRun     # 2. (LB 확인하려면 한 번 더 실행해 2개)
./gradlew :member-service:bootRun    # 3.
./gradlew :api-gateway:bootRun       # 4. 마지막
```
Eureka 대시보드: http://localhost:8761

> 참고: 인스턴스를 새로 띄워도 Gateway가 즉시 분산하지 않는다.
> Eureka 레지스트리 페치(기본 30초) + LoadBalancer 캐시(기본 35초) 때문에 최대 ~65초 지연이 있다.

## 검증 (curl)

```bash
# 1) 토큰 없이 보호된 경로 → 401
curl -i http://localhost:8000/order/health

# 2) 로그인으로 토큰 발급 (화이트리스트, 인증 불필요)
TOKEN=$(curl -s -X POST http://localhost:8000/member/login \
  -H "Content-Type: application/json" -d '{"id":"test"}' \
  | sed 's/.*"token":"\([^"]*\)".*/\1/')

# 3) 토큰으로 호출 → 200, requestedBy=test (X-User-Id 전달 확인)
curl -s http://localhost:8000/order/1 -H "Authorization: Bearer $TOKEN"

# 4) 로드밸런싱: 여러 번 호출하면 handledByPort 가 번갈아 바뀜
for i in 1 2 3 4; do curl -s http://localhost:8000/order/health -H "Authorization: Bearer $TOKEN"; echo; done
```

## 테스트

```bash
./gradlew test   # member-service(JwtProvider), api-gateway(JwtValidator) 단위 테스트
```

## Phase 2 (예정)

- **Rate Limiting** — Redis 기반 요청 수 제한
- **회복탄력성** — Resilience4j CircuitBreaker / 타임아웃 / fallback

## 핵심 개념 메모

- **API Gateway = North-South(외부↔시스템) 단일 진입점.** 라우팅 + 공통 관심사(인증·로깅·rate limit)를 중앙 처리.
- 내부 서비스 간(East-West) 통신은 Gateway를 거치지 않음 → Service Discovery / Service Mesh 영역.
- 공통 기능을 별도 서비스로 추출해 여러 서비스가 호출하는 형태 = **Shared Service** / DDD의 **Open Host Service**.
- 필터 순서: 로깅(HIGHEST) → 인증(HIGHEST+1) → 라우팅. 401로 막힌 요청도 로깅엔 남는다.
- `server.port=0`(랜덤) 환경에서 실제 포트는 `WebServerInitializedEvent`로 얻는다(`@Value("${server.port}")`는 "0" 반환).
