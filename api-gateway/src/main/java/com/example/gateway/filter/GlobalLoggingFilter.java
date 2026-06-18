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
