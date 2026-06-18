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
