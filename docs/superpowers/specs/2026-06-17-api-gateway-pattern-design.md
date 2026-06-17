# API Gateway 패턴 학습 프로젝트 설계

**작성일**: 2026-06-17
**목적**: MSA의 API Gateway 패턴을 직접 구현하며 학습한다. Spring Cloud Gateway + Eureka 기반으로, 라우팅/로드밸런싱, 글로벌 로깅 필터, JWT 인증 필터를 단계별로 구현하고 실행·검증한다.

---

## 1. 배경 & 학습 목표

MSA에서 서비스가 여러 개로 분리되면 클라이언트가 각 서비스를 직접 호출할 때 다음 문제가 생긴다.

- 클라이언트가 모든 서비스 주소를 알아야 함
- 인증/인가, 로깅, CORS, rate limit 등 공통 관심사를 서비스마다 중복 구현
- 서비스 추가/주소 변경 시 클라이언트도 변경 필요

**API Gateway 패턴**은 모든 외부 요청을 받는 **단일 진입점(single entry point)**을 두어,
라우팅 + 공통 관심사(cross-cutting concerns)를 한 곳에서 처리한다. (North-South 트래픽 담당)

### 학습 목표
- Spring Cloud Gateway의 라우팅·로드밸런싱 동작 이해
- Gateway 필터 체인(리액티브 WebFlux) 동작 원리 이해
- 공통 관심사(로깅, 인증)를 Gateway에서 중앙 처리하는 방법 습득
- Eureka 서비스 디스커버리 연동 이해

---

## 2. 기술 스택

- **빌드**: Gradle 멀티모듈 (한 저장소에 모든 모듈)
- **Spring Boot 3.3.x / Java 17**
- **Spring Cloud 2023.0.x**
- **Spring Cloud Gateway** (WebFlux 리액티브)
- **Spring Cloud Netflix Eureka** (서비스 디스커버리)
- JWT: `jjwt` (HS256 대칭키)

> 기존 프로젝트 컨벤션(Spring Boot 3.1.x / Java 17 / Spring Cloud 2022.x / Gradle)을 약간 최신화하여 채택.

---

## 3. 아키텍처

### 모듈 구성 (Phase 1)
```
msa-gateway-practice/
├── settings.gradle              # 멀티모듈 정의
├── build.gradle                 # 공통 설정
├── eureka-server/               # 서비스 등록/발견 (:8761)
├── api-gateway/                 # Gateway (:8000) ★핵심
│   ├── 라우팅 설정 (lb://)
│   ├── 글로벌 로깅 필터
│   └── JWT 인증 필터
├── order-service/               # 더미 서비스 (Eureka 등록)
└── member-service/              # 더미 서비스 + JWT 발급(login)
```

### 트래픽 흐름
```
[클라이언트]
    │ ① POST /member/login (인증 불필요) → JWT 발급
    │ ② GET /order/** + Authorization: Bearer <JWT>
    ▼
[API Gateway :8000]
    ├─ 로깅 필터: 요청ID(UUID) 부여, 요청/응답 로깅
    ├─ JWT 인증 필터: 토큰 검증 (없거나 틀리면 401 차단)
    └─ 라우팅: Eureka에서 대상 서비스 찾아 lb://로 전달
    ▼
[order-service / member-service]  ← Eureka(:8761)에 자기 주소 등록
```

**핵심**: 클라이언트는 8000번 포트(Gateway)만 알면 되고, 내부 서비스 주소·인증·로깅을 전부 Gateway가 처리한다.

---

## 4. 모듈 상세

### 4.1 eureka-server
- `@EnableEurekaServer`. 서비스 주소 등록용 "전화번호부".
- `:8761` 대시보드에서 등록된 서비스 확인.

### 4.2 order-service (더미)
- `@EnableDiscoveryClient` 로 Eureka 자동 등록.
- `GET /order/health`, `GET /order/{id}` 간단 응답.
- 응답에 처리한 인스턴스 포트 포함 → 로드밸런싱 확인용.

### 4.3 member-service (더미)
- `@EnableDiscoveryClient` 로 Eureka 자동 등록.
- `POST /member/login` → 아이디 받아 JWT 발급 (Gateway 인증 테스트용).

### 4.4 api-gateway ★핵심

**(1) 라우팅 + 로드밸런싱** — `application.yml` 설정 기반
```yaml
routes:
  - id: order-service
    uri: lb://ORDER-SERVICE      # Eureka 이름으로 로드밸런싱
    predicates:
      - Path=/order/**
  - id: member-service
    uri: lb://MEMBER-SERVICE
    predicates:
      - Path=/member/**
```

**(2) 글로벌 로깅 필터** — `GlobalFilter` 구현
- 모든 요청에 요청ID(UUID) 부여 → 헤더로 다운스트림 전달
- 요청 시 `[요청ID] 메서드 경로` 로깅, 응답 시 `[요청ID] 상태코드 소요시간ms` 로깅
- 학습 포인트: 필터 체인의 리액티브(Mono/Flux) 동작

**(3) JWT 인증 필터** — `GlobalFilter` (또는 `AbstractGatewayFilterFactory`)
- `Authorization: Bearer <token>` 헤더 검사
- 토큰 없음/만료/위조 → **401 즉시 차단** (다운스트림 호출 안 함)
- 검증 성공 → 사용자 정보를 `X-User-Id` 헤더로 다운스트림 전달
- **화이트리스트**: `/member/login` 은 인증 제외

**필터 실행 순서**
```
요청 → [로깅 필터(요청)] → [JWT 인증 필터] → 라우팅 → 서비스
응답 ← [로깅 필터(응답)] ←───────────────────────────┘
```

**JWT 검증 방식 (학습 단순화)**
- 대칭키(HS256) + 공유 시크릿. member-service 발급, gateway가 같은 시크릿으로 검증.
- 실무에선 비대칭키(RS256)/공개키 방식이 일반적 → 확장 가능하도록 주석 명시.

---

## 5. 개발 순서

각 단계가 눈으로 확인 가능한 결과물을 내도록 분할한다.

```
1단계: 멀티모듈 골격 (settings.gradle, build.gradle)
2단계: eureka-server  → :8761 대시보드 확인
3단계: order-service  → Eureka 등록 확인
4단계: api-gateway 라우팅+LB → 8000 통해 order 호출      [기능 1 완료]
5단계: 글로벌 로깅 필터 → 콘솔에 요청ID·소요시간 로그       [기능 2 완료]
6단계: member-service 로그인 → JWT 발급 확인
7단계: gateway JWT 인증 필터 → 토큰 없으면 401, 있으면 통과 [기능 3 완료]
```

---

## 6. 검증 방법

- **Eureka**: 브라우저 `localhost:8761` → 등록 서비스 목록 확인
- **라우팅/LB**: `curl localhost:8000/order/health` → order-service 응답 + 처리 포트 확인.
  order-service 2개 띄우면 번갈아 응답(LB) 확인
- **로깅**: Gateway 콘솔에 `[uuid] GET /order/health → 200 (12ms)` 형태 로그
- **JWT**:
  - `curl localhost:8000/order/health` (토큰 없음) → **401**
  - `curl -X POST localhost:8000/member/login -d '{"id":"test"}'` → JWT 수신
  - `curl localhost:8000/order/health -H "Authorization: Bearer <JWT>"` → **200**

---

## 7. 문서화

- 루트 `README.md`: 아키텍처 그림, 모듈 설명, 실행 순서, curl 검증 명령 정리 (복습/설명용)

---

## 8. Phase 2 (이후 확장)

Phase 1 구조를 그대로 확장한다.

- **4. Rate Limiting** — Redis 기반 요청 수 제한
- **5. 회복탄력성(Resilience4j)** — CircuitBreaker / 타임아웃 / fallback

---

## 9. 범위(Scope) 정리

| 포함 (Phase 1) | 제외 (Phase 2 이후) |
|----------------|---------------------|
| Gradle 멀티모듈 | Rate Limiting (Redis) |
| eureka-server | Resilience4j |
| api-gateway: 라우팅+LB, 로깅 필터, JWT 인증 필터 | 비대칭키(RS256) JWT |
| order-service, member-service (더미) | Config Server 연동 |
| 단계별 실행·검증, README | 실제 DB/영속성 |
