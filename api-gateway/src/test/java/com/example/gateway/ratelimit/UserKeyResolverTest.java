package com.example.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class UserKeyResolverTest {

    private final UserKeyResolver resolver = new UserKeyResolver();

    @Test
    void X_User_Id_헤더가_있으면_그_값을_레이트리밋_키로_사용한다() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/order/1").header("X-User-Id", "user-7"));

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("user-7");
    }

    @Test
    void 헤더가_없으면_클라이언트_IP를_키로_사용한다() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/member/login")
                        .remoteAddress(new InetSocketAddress("10.1.2.3", 54321)));

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("10.1.2.3");
    }

    @Test
    void 헤더도_IP도_없으면_anonymous를_키로_사용한다() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/member/login"));

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("anonymous");
    }
}
