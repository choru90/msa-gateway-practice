# API Gateway 패턴 학습 프로젝트 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Spring Cloud Gateway + Eureka로 라우팅/로드밸런싱 + 글로벌 로깅 필터 + JWT 인증 필터를 갖춘 API Gateway 학습 프로젝트(Phase 1)를 단계별로 구현한다.

**Architecture:** Gradle 멀티모듈 단일 저장소. `eureka-server`가 서비스 레지스트리, `order-service`/`member-service`가 Eureka에 등록되는 더미 서비스, `api-gateway`가 모든 외부 요청의 단일 진입점으로 라우팅·로깅·인증을 담당한다. 클라이언트는 Gateway(:8000)만 호출하고, Gateway는 Eureka에서 대상 서비스를 찾아 `lb://`로 전달한다.

**Tech Stack:** Java 17, Gradle (멀티모듈), Spring Boot 3.3.4, Spring Cloud 2023.0.3, Spring Cloud Gateway(WebFlux), Spring Cloud Netflix Eureka, jjwt 0.12.6 (HS256), Lombok.

---

## 파일 구조 (생성 대상)

```
msa-gateway-practice/
├── settings.gradle                  # 멀티모듈 정의
├── build.gradle                     # 루트 공통 설정 (Spring Cloud BOM)
├── gradlew, gradlew.bat, gradle/    # Gradle wrapper
├── eureka-server/
│   ├── build.gradle
│   └── src/main/
│       ├── java/com/example/eureka/EurekaServerApplication.java
│       └── resources/application.yml
├── order-service/
│   ├── build.gradle
│   └── src/main/
│       ├── java/com/example/order/OrderServiceApplication.java
│       ├── java/com/example/order/OrderController.java
│       └── resources/application.yml
├── member-service/
│   ├── build.gradle
│   └── src/
│       ├── main/java/com/example/member/MemberServiceApplication.java
│       ├── main/java/com/example/member/JwtProvider.java
│       ├── main/java/com/example/member/MemberController.java
│       ├── main/resources/application.yml
│       └── test/java/com/example/member/JwtProviderTest.java
├── api-gateway/
│   ├── build.gradle
│   └── src/
│       ├── main/java/com/example/gateway/ApiGatewayApplication.java
│       ├── main/java/com/example/gateway/filter/GlobalLoggingFilter.java
│       ├── main/java/com/example/gateway/jwt/JwtValidator.java
│       ├── main/java/com/example/gateway/filter/JwtAuthenticationFilter.java
│       ├── main/resources/application.yml
│       └── test/java/com/example/gateway/jwt/JwtValidatorTest.java
└── README.md
```

**테스트 전략:** JWT 발급/검증 같은 순수 로직은 단위 테스트(TDD)로 검증한다. Gateway 라우팅·필터는 여러 서비스가 함께 떠야 하는 통합 동작이라, 각 Task 끝에서 **앱을 실제로 실행하고 curl로 검증**한다(스펙 6장). 각 검증 단계에 기대 출력까지 명시한다.

---

## Task 0: 멀티모듈 골격 + Gradle Wrapper

**Files:**
- Create: `settings.gradle`
- Create: `build.gradle`
- Create: Gradle wrapper (`gradlew`, `gradle/wrapper/*`)

- [ ] **Step 1: Gradle wrapper 생성**

저장소 루트에서 실행 (시스템 gradle 사용):

Run: `cd /Users/jeong-gwangcheol/IdeaProjects/msa-gateway-practice && gradle wrapper --gradle-version 8.10`
Expected: `BUILD SUCCESSFUL`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` 생성됨

- [ ] **Step 2: `settings.gradle` 작성**

```groovy
rootProject.name = 'msa-gateway-practice'

include 'eureka-server'
include 'order-service'
include 'member-service'
include 'api-gateway'
```

- [ ] **Step 3: 루트 `build.gradle` 작성**

```groovy
plugins {
    id 'org.springframework.boot' version '3.3.4' apply false
    id 'io.spring.dependency-management' version '1.1.6'
}

allprojects {
    group = 'com.example'
    version = '0.0.1-SNAPSHOT'
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply plugin: 'java'
    apply plugin: 'org.springframework.boot'
    apply plugin: 'io.spring.dependency-management'

    java {
        sourceCompatibility = '17'
    }

    ext {
        set('springCloudVersion', '2023.0.3')
    }

    dependencies {
        compileOnly 'org.projectlombok:lombok'
        annotationProcessor 'org.projectlombok:lombok'
        testImplementation 'org.springframework.boot:spring-boot-starter-test'
        testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    }

    dependencyManagement {
        imports {
            mavenBom "org.springframework.cloud:spring-cloud-dependencies:${springCloudVersion}"
        }
    }

    tasks.named('test') {
        useJUnitPlatform()
    }
}
```

- [ ] **Step 4: 빌드 확인 (모듈은 비어있어도 설정이 올바른지 확인)**

Run: `./gradlew projects`
Expected: `Root project 'msa-gateway-practice'` 아래에 `eureka-server`, `order-service`, `member-service`, `api-gateway` 4개 하위 프로젝트가 표시됨. `BUILD SUCCESSFUL`
(주의: 각 모듈 디렉터리/build.gradle이 아직 없으면 `include`된 모듈 경고가 날 수 있음. Task 1~ 진행하면서 채워짐. 경고만 나고 `projects`는 성공해야 함.)

- [ ] **Step 5: Commit**

```bash
git add settings.gradle build.gradle gradlew gradlew.bat gradle/
git commit -m "build: Gradle 멀티모듈 골격 + wrapper 구성"
```

---

## Task 1: eureka-server (서비스 레지스트리)

**Files:**
- Create: `eureka-server/build.gradle`
- Create: `eureka-server/src/main/java/com/example/eureka/EurekaServerApplication.java`
- Create: `eureka-server/src/main/resources/application.yml`

- [ ] **Step 1: `eureka-server/build.gradle` 작성**

```groovy
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-server'
}
```

- [ ] **Step 2: `EurekaServerApplication.java` 작성**

```java
package com.example.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@EnableEurekaServer
@SpringBootApplication
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

- [ ] **Step 3: `eureka-server/src/main/resources/application.yml` 작성**

```yaml
server:
  port: 8761

spring:
  application:
    name: eureka-server

eureka:
  client:
    # 레지스트리 자신은 다른 Eureka에 등록하거나 정보를 가져올 필요가 없음
    register-with-eureka: false
    fetch-registry: false
```

- [ ] **Step 4: 실행 후 대시보드 확인**

Run: `./gradlew :eureka-server:bootRun`
브라우저에서 `http://localhost:8761` 접속
Expected: Eureka 대시보드 화면이 뜨고, "Instances currently registered with Eureka" 목록이 비어있음(아직 등록 서비스 없음). 확인 후 `Ctrl+C`로 종료.

- [ ] **Step 5: Commit**

```bash
git add eureka-server/
git commit -m "feat: eureka-server 서비스 레지스트리 추가"
```

---

## Task 2: order-service (Eureka 등록 더미 서비스)

**Files:**
- Create: `order-service/build.gradle`
- Create: `order-service/src/main/java/com/example/order/OrderServiceApplication.java`
- Create: `order-service/src/main/java/com/example/order/OrderController.java`
- Create: `order-service/src/main/resources/application.yml`

- [ ] **Step 1: `order-service/build.gradle` 작성**

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
}
```

- [ ] **Step 2: `OrderServiceApplication.java` 작성**

```java
package com.example.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```
(참고: Spring Cloud 최신 버전은 eureka-client가 classpath에 있으면 `@EnableDiscoveryClient` 없이도 자동 등록된다. 명시적 학습을 위해 붙여도 무방하나 필수는 아니다.)

- [ ] **Step 3: `OrderController.java` 작성** — 처리한 인스턴스 포트를 응답에 포함(LB 확인용)

```java
package com.example.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/order")
public class OrderController {

    @Value("${server.port}")
    private String port;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "service", "order-service",
                "port", port,
                "status", "UP"
        );
    }

    @GetMapping("/{id}")
    public Map<String, String> getOrder(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        // X-User-Id는 Gateway의 JWT 인증 필터가 토큰에서 추출해 넣어주는 헤더 (Task 6)
        return Map.of(
                "orderId", id,
                "handledByPort", port,
                "requestedBy", userId == null ? "unknown" : userId
        );
    }
}
```

- [ ] **Step 4: `order-service/src/main/resources/application.yml` 작성**

```yaml
server:
  port: 0   # 랜덤 포트 (인스턴스 여러 개 띄워 LB 확인하기 위함)

spring:
  application:
    name: order-service   # Eureka 등록 이름 → Gateway에서 lb://ORDER-SERVICE 로 참조

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    # 랜덤 포트 사용 시 인스턴스 ID가 겹치지 않도록 고유화
    instance-id: ${spring.application.name}:${random.uuid}
```

- [ ] **Step 5: 실행 후 Eureka 등록 확인** (eureka-server를 먼저 실행해 둔 상태에서)

터미널 A: `./gradlew :eureka-server:bootRun`
터미널 B: `./gradlew :order-service:bootRun`
브라우저 `http://localhost:8761` 새로고침
Expected: 등록 목록에 `ORDER-SERVICE`가 `UP (1)` 로 표시됨.

- [ ] **Step 6: 직접 호출 확인** (order-service가 받은 랜덤 포트는 콘솔 로그의 `Tomcat started on port(s): XXXXX` 에서 확인)

Run: `curl http://localhost:<랜덤포트>/order/health`
Expected: `{"service":"order-service","port":"<랜덤포트>","status":"UP"}`
확인 후 두 터미널 모두 `Ctrl+C`.

- [ ] **Step 7: Commit**

```bash
git add order-service/
git commit -m "feat: order-service 더미 서비스 (Eureka 등록, 포트 응답 포함)"
```

---

## Task 3: api-gateway — 라우팅 + 로드밸런싱 [기능 1]

**Files:**
- Create: `api-gateway/build.gradle`
- Create: `api-gateway/src/main/java/com/example/gateway/ApiGatewayApplication.java`
- Create: `api-gateway/src/main/resources/application.yml`

- [ ] **Step 1: `api-gateway/build.gradle` 작성**

```groovy
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
}
```
(참고: gateway는 WebFlux 기반이라 `spring-boot-starter-web`을 넣으면 안 된다. 충돌난다. eureka-client에 reactive loadbalancer가 포함되어 `lb://`가 동작한다.)

- [ ] **Step 2: `ApiGatewayApplication.java` 작성**

```java
package com.example.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

- [ ] **Step 3: `api-gateway/src/main/resources/application.yml` 작성** — 라우팅 설정

```yaml
server:
  port: 8000

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      discovery:
        locator:
          enabled: false   # 명시적 라우팅만 사용 (자동 라우팅 비활성화)
      routes:
        - id: order-service
          uri: lb://ORDER-SERVICE   # Eureka 이름으로 로드밸런싱
          predicates:
            - Path=/order/**

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

- [ ] **Step 4: 실행 후 Gateway 경유 라우팅 확인** (eureka-server, order-service 먼저 실행)

터미널 A: `./gradlew :eureka-server:bootRun`
터미널 B: `./gradlew :order-service:bootRun`
터미널 C: `./gradlew :api-gateway:bootRun`
(Gateway가 Eureka에서 ORDER-SERVICE를 받아오기까지 수 초 걸릴 수 있음)

Run: `curl http://localhost:8000/order/health`
Expected: `{"service":"order-service","port":"<랜덤포트>","status":"UP"}` — **8000(Gateway)으로 호출했는데 order-service 응답이 돌아옴**.

- [ ] **Step 5: 로드밸런싱 확인** (order-service 인스턴스 2개)

터미널 D 추가: `./gradlew :order-service:bootRun`
Eureka 대시보드에 `ORDER-SERVICE (2)` 확인 후:

Run: `curl http://localhost:8000/order/health` 를 여러 번 반복
Expected: 응답의 `port` 값이 두 인스턴스 포트 사이에서 번갈아 바뀜(라운드로빈 로드밸런싱). 확인 후 모든 터미널 종료.

- [ ] **Step 6: Commit**

```bash
git add api-gateway/
git commit -m "feat: api-gateway 라우팅 + Eureka 로드밸런싱 (기능 1)"
```

---

## Task 4: api-gateway — 글로벌 로깅 필터 [기능 2]

**Files:**
- Create: `api-gateway/src/main/java/com/example/gateway/filter/GlobalLoggingFilter.java`

- [ ] **Step 1: `GlobalLoggingFilter.java` 작성** — 요청ID 부여 + 요청/응답 로깅

```java
package com.example.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
public class GlobalLoggingFilter implements GlobalFilter, Ordered {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1) 요청ID 부여 → 다운스트림 서비스까지 추적 가능하도록 헤더에 실음
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(REQUEST_ID_HEADER, requestId)
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        long startTime = System.currentTimeMillis();
        log.info("[{}] --> {} {}", requestId,
                mutatedRequest.getMethod(), mutatedRequest.getURI().getPath());

        // 2) 체인 실행 후(then) 응답 로깅 — 리액티브: 응답이 끝난 시점에 실행됨
        return chain.filter(mutatedExchange).then(Mono.fromRunnable(() -> {
            long took = System.currentTimeMillis() - startTime;
            var statusCode = mutatedExchange.getResponse().getStatusCode();
            log.info("[{}] <-- {} ({}ms)", requestId, statusCode, took);
        }));
    }

    @Override
    public int getOrder() {
        // 가장 먼저 실행되어 전체 요청을 감싸도록 높은 우선순위
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
```

- [ ] **Step 2: 실행 후 로그 확인** (eureka-server, order-service, api-gateway 실행)

Run: `curl http://localhost:8000/order/health`
api-gateway 콘솔(터미널 C) 확인
Expected: 두 줄의 로그가 찍힘
```
[xxxxxxxx] --> GET /order/health
[xxxxxxxx] <-- 200 OK (NNms)
```
(같은 `xxxxxxxx` 요청ID로 요청·응답이 짝지어짐)

- [ ] **Step 3: 요청ID가 다운스트림까지 전달되는지 확인** — order-service의 `/order/{id}` 응답에는 직접 안 나오지만, 헤더 전달은 다음 Task에서 X-User-Id로 함께 검증됨. 여기서는 Gateway 로그 짝맞춤만 확인하면 충분.

- [ ] **Step 4: Commit**

```bash
git add api-gateway/src/main/java/com/example/gateway/filter/GlobalLoggingFilter.java
git commit -m "feat: api-gateway 글로벌 로깅 필터 (요청ID + 요청/응답 로깅, 기능 2)"
```

---

## Task 5: member-service — 로그인 + JWT 발급 (TDD)

**Files:**
- Create: `member-service/build.gradle`
- Create: `member-service/src/main/java/com/example/member/JwtProvider.java`
- Create: `member-service/src/test/java/com/example/member/JwtProviderTest.java`
- Create: `member-service/src/main/java/com/example/member/MemberController.java`
- Create: `member-service/src/main/java/com/example/member/MemberServiceApplication.java`
- Create: `member-service/src/main/resources/application.yml`

- [ ] **Step 1: `member-service/build.gradle` 작성**

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'

    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
}
```

- [ ] **Step 2: 실패하는 테스트 작성 — `JwtProviderTest.java`**

```java
package com.example.member;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    // HS256은 최소 256bit(32byte) 키 필요
    private final String secret = "my-test-secret-key-for-jwt-hs256-minimum-32-bytes!!";
    private final JwtProvider provider = new JwtProvider(secret, 3600_000L);

    @Test
    void createToken_은_subject로_userId를_담는다() {
        String token = provider.createToken("user-42");

        assertThat(token).isNotBlank();

        // 같은 시크릿으로 파싱해서 subject 확인
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();

        assertThat(claims.getSubject()).isEqualTo("user-42");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew :member-service:test --tests com.example.member.JwtProviderTest`
Expected: 컴파일 실패 — `JwtProvider` 클래스가 없음 (`cannot find symbol`).

- [ ] **Step 4: `JwtProvider.java` 구현**

```java
package com.example.member;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final long validityMillis;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-millis}") long validityMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityMillis = validityMillis;
    }

    public String createToken(String userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMillis);
        return Jwts.builder()
                .subject(userId)        // sub = userId
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)          // HS256 (키 길이로 알고리즘 자동 결정)
                .compact();
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :member-service:test --tests com.example.member.JwtProviderTest`
Expected: `BUILD SUCCESSFUL`, 테스트 1개 통과.

- [ ] **Step 6: `MemberController.java` 작성 — 로그인 API**

```java
package com.example.member;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/member")
public class MemberController {

    private final JwtProvider jwtProvider;

    public MemberController(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    // 학습용: 비밀번호 검증 없이 id만 받아 토큰 발급
    @PostMapping("/login")
    public Map<String, String> login(@RequestBody Map<String, String> body) {
        String id = body.getOrDefault("id", "anonymous");
        String token = jwtProvider.createToken(id);
        return Map.of("userId", id, "token", token);
    }
}
```

- [ ] **Step 7: `MemberServiceApplication.java` 작성**

```java
package com.example.member;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MemberServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MemberServiceApplication.class, args);
    }
}
```

- [ ] **Step 8: `member-service/src/main/resources/application.yml` 작성**

```yaml
server:
  port: 0

spring:
  application:
    name: member-service

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    instance-id: ${spring.application.name}:${random.uuid}

jwt:
  # Gateway(JwtValidator)와 반드시 동일한 시크릿이어야 검증 가능 (대칭키 HS256)
  secret: my-super-secret-key-for-jwt-hs256-which-is-at-least-32-bytes-long
  expiration-millis: 3600000   # 1시간
```

- [ ] **Step 9: 실행 후 JWT 발급 확인** (eureka-server, member-service 실행)

Run: `curl -X POST http://localhost:8000/member/login -H "Content-Type: application/json" -d '{"id":"test"}'`
(아직 Gateway에 member 라우트가 없으므로, 우선 member-service 직접 포트로 호출:
`curl -X POST http://localhost:<member랜덤포트>/member/login -H "Content-Type: application/json" -d '{"id":"test"}'`)
Expected: `{"userId":"test","token":"eyJhbGci...<긴 JWT 문자열>"}`

- [ ] **Step 10: Commit**

```bash
git add member-service/
git commit -m "feat: member-service 로그인 + JWT(HS256) 발급 (TDD)"
```

---

## Task 6: api-gateway — JWT 인증 필터 [기능 3] (TDD)

**Files:**
- Modify: `api-gateway/src/main/resources/application.yml` (member 라우트 + jwt 시크릿 추가)
- Create: `api-gateway/src/main/java/com/example/gateway/jwt/JwtValidator.java`
- Create: `api-gateway/src/test/java/com/example/gateway/jwt/JwtValidatorTest.java`
- Create: `api-gateway/src/main/java/com/example/gateway/filter/JwtAuthenticationFilter.java`
- Modify: `api-gateway/build.gradle` (jjwt 추가)

- [ ] **Step 1: `api-gateway/build.gradle`에 jjwt 의존성 추가**

```groovy
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'

    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
}
```

- [ ] **Step 2: 실패하는 테스트 작성 — `JwtValidatorTest.java`**

```java
package com.example.gateway.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtValidatorTest {

    private final String secret = "my-super-secret-key-for-jwt-hs256-which-is-at-least-32-bytes-long";
    private final JwtValidator validator = new JwtValidator(secret);
    private final SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

    private String tokenWith(String subject, long ttlMillis) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key)
                .compact();
    }

    @Test
    void 유효한_토큰이면_userId를_반환한다() {
        String token = tokenWith("user-7", 60_000);
        assertThat(validator.validateAndGetUserId(token)).isEqualTo("user-7");
    }

    @Test
    void 만료된_토큰이면_null을_반환한다() {
        String token = tokenWith("user-7", -1_000); // 이미 만료
        assertThat(validator.validateAndGetUserId(token)).isNull();
    }

    @Test
    void 시크릿이_다르면_null을_반환한다() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "a-completely-different-secret-key-also-32-bytes-long!!".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder().subject("hacker")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey).compact();
        assertThat(validator.validateAndGetUserId(forged)).isNull();
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew :api-gateway:test --tests com.example.gateway.jwt.JwtValidatorTest`
Expected: 컴파일 실패 — `JwtValidator` 없음.

- [ ] **Step 4: `JwtValidator.java` 구현**

```java
package com.example.gateway.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtValidator {

    private final SecretKey key;

    public JwtValidator(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 토큰이 유효하면 userId(subject)를, 유효하지 않으면(만료/위조/형식오류) null을 반환.
     */
    public String validateAndGetUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            // 만료(ExpiredJwtException), 서명불일치(SignatureException), 형식오류 등 모두 무효 처리
            return null;
        }
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :api-gateway:test --tests com.example.gateway.jwt.JwtValidatorTest`
Expected: `BUILD SUCCESSFUL`, 테스트 3개 통과.

- [ ] **Step 6: `JwtAuthenticationFilter.java` 작성** — 화이트리스트 + 401 차단 + X-User-Id 전달

```java
package com.example.gateway.filter;

import com.example.gateway.jwt.JwtValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtValidator jwtValidator;

    // 인증 없이 통과시킬 경로 (로그인 등)
    private static final List<String> WHITELIST = List.of("/member/login");

    public JwtAuthenticationFilter(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1) 화이트리스트는 인증 건너뛰기
        if (WHITELIST.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        // 2) Authorization 헤더 확인
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "missing or malformed Authorization header");
        }

        // 3) 토큰 검증
        String token = authHeader.substring(7);
        String userId = jwtValidator.validateAndGetUserId(token);
        if (userId == null) {
            return unauthorized(exchange, "invalid or expired token");
        }

        // 4) 검증 성공 → userId를 다운스트림 헤더로 전달
        ServerHttpRequest mutated = request.mutate()
                .header("X-User-Id", userId)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        log.warn("401 Unauthorized: {} ({})", exchange.getRequest().getURI().getPath(), reason);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete(); // 다운스트림 호출 없이 즉시 종료
    }

    @Override
    public int getOrder() {
        // 로깅 필터(HIGHEST_PRECEDENCE) 다음, 라우팅 전에 실행
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
```

- [ ] **Step 7: `api-gateway/src/main/resources/application.yml` 수정** — member 라우트 + jwt 시크릿 추가

```yaml
server:
  port: 8000

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      discovery:
        locator:
          enabled: false
      routes:
        - id: order-service
          uri: lb://ORDER-SERVICE
          predicates:
            - Path=/order/**
        - id: member-service
          uri: lb://MEMBER-SERVICE
          predicates:
            - Path=/member/**

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/

jwt:
  # member-service와 반드시 동일한 시크릿 (대칭키 HS256)
  secret: my-super-secret-key-for-jwt-hs256-which-is-at-least-32-bytes-long
```

- [ ] **Step 8: 전체 실행 후 인증 흐름 검증** (eureka-server, order-service, member-service, api-gateway 모두 실행)

검증 1 — 토큰 없이 보호된 경로 호출:
Run: `curl -i http://localhost:8000/order/health`
Expected: `HTTP/1.1 401 Unauthorized`, 본문 없음. gateway 콘솔에 `401 Unauthorized: /order/health (missing or malformed Authorization header)`

검증 2 — 로그인으로 토큰 발급(화이트리스트, 인증 불필요):
Run: `curl -X POST http://localhost:8000/member/login -H "Content-Type: application/json" -d '{"id":"test"}'`
Expected: `{"userId":"test","token":"eyJ..."}`

검증 3 — 발급받은 토큰으로 보호된 경로 호출:
```bash
TOKEN=$(curl -s -X POST http://localhost:8000/member/login -H "Content-Type: application/json" -d '{"id":"test"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl -s http://localhost:8000/order/1 -H "Authorization: Bearer $TOKEN"
```
Expected: `{"orderId":"1","handledByPort":"<포트>","requestedBy":"test"}` — **X-User-Id가 토큰의 subject(test)로 채워져 다운스트림까지 전달됨**.

검증 4 — 위조 토큰:
Run: `curl -i http://localhost:8000/order/health -H "Authorization: Bearer not-a-real-token"`
Expected: `HTTP/1.1 401 Unauthorized`.

- [ ] **Step 9: 전체 테스트 실행**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`, member-service·api-gateway 단위 테스트 모두 통과.

- [ ] **Step 10: Commit**

```bash
git add api-gateway/
git commit -m "feat: api-gateway JWT 인증 필터 (화이트리스트/401 차단/X-User-Id 전달, 기능 3)"
```

---

## Task 7: README 작성

**Files:**
- Create: `README.md`

- [ ] **Step 1: `README.md` 작성**

````markdown
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
| member-service | 랜덤 | 로그인 → JWT(HS256) 발급 |

## 구현된 기능 (Phase 1)

1. **라우팅 + 로드밸런싱** — Eureka에 등록된 서비스로 `lb://` 라우팅
2. **글로벌 로깅 필터** — 모든 요청에 요청ID 부여, 요청/응답·소요시간 로깅
3. **JWT 인증 필터** — Bearer 토큰 검증, 실패 시 401 차단, 성공 시 `X-User-Id` 전달
   (화이트리스트: `/member/login`)

## 실행 순서

각각 별도 터미널에서:
```bash
./gradlew :eureka-server:bootRun     # 1. 레지스트리
./gradlew :order-service:bootRun     # 2. (LB 확인하려면 한 번 더 실행해 2개)
./gradlew :member-service:bootRun    # 3.
./gradlew :api-gateway:bootRun       # 4. 마지막
```
Eureka 대시보드: http://localhost:8761

## 검증 (curl)

```bash
# 1) 토큰 없이 보호된 경로 → 401
curl -i http://localhost:8000/order/health

# 2) 로그인으로 토큰 발급
TOKEN=$(curl -s -X POST http://localhost:8000/member/login \
  -H "Content-Type: application/json" -d '{"id":"test"}' \
  | sed 's/.*"token":"\([^"]*\)".*/\1/')

# 3) 토큰으로 호출 → 200, requestedBy=test
curl -s http://localhost:8000/order/1 -H "Authorization: Bearer $TOKEN"

# 4) 로드밸런싱: 여러 번 호출하면 handledByPort 가 번갈아 바뀜
for i in 1 2 3 4; do curl -s http://localhost:8000/order/health -H "Authorization: Bearer $TOKEN"; echo; done
```

## Phase 2 (예정)

- Rate Limiting (Redis 기반 요청 수 제한)
- 회복탄력성: Resilience4j CircuitBreaker / 타임아웃 / fallback

## 핵심 개념 메모

- **API Gateway = North-South(외부↔시스템) 단일 진입점.** 라우팅 + 공통 관심사(인증·로깅·rate limit)를 중앙 처리.
- 내부 서비스 간(East-West) 통신은 Gateway를 거치지 않음 → Service Discovery / Service Mesh 영역.
- 공통 기능을 별도 서비스로 추출해 여러 서비스가 호출하는 형태 = Shared Service / DDD의 Open Host Service.
````

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: README (아키텍처/실행/검증/개념 정리)"
```

---

## 완료 기준 (Definition of Done)

- [ ] `./gradlew test` 전체 통과 (member-service, api-gateway 단위 테스트)
- [ ] eureka-server :8761 대시보드에 order-service, member-service 등록 확인
- [ ] 토큰 없이 `/order/**` 호출 시 401
- [ ] `/member/login` 으로 JWT 발급, 해당 토큰으로 `/order/**` 호출 시 200 + `requestedBy`에 userId
- [ ] order-service 2개 인스턴스에서 `handledByPort` 라운드로빈 확인
- [ ] gateway 콘솔에 요청ID 기반 요청/응답 로그 출력
- [ ] README의 curl 검증 명령이 그대로 동작
```
