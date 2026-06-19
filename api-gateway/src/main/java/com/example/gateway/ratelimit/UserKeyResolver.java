package com.example.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * RequestRateLimiter가 "누구"를 기준으로 토큰 버킷을 나눌지 결정하는 KeyResolver.
 *
 * <p>우선순위:
 * <ol>
 *   <li>{@code X-User-Id} 헤더(JWT 인증 필터가 채워줌) → 사용자별 제한</li>
 *   <li>없으면 클라이언트 IP → 비로그인(/member/login 등) 요청을 IP별 제한</li>
 *   <li>둘 다 없으면 {@code anonymous} 단일 버킷</li>
 * </ol>
 */
@Component
public class UserKeyResolver implements KeyResolver {

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return Mono.just(userId);
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            return Mono.just(remote.getAddress().getHostAddress());
        }
        return Mono.just("anonymous");
    }
}
