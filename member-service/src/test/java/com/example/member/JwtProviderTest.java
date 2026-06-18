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
