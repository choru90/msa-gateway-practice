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
