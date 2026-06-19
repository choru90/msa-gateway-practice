package com.example.gateway.fallback;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackControllerTest {

    private final FallbackController controller = new FallbackController();

    @Test
    void order_fallback는_503과_DEGRADED_상태를_반환한다() {
        ResponseEntity<Map<String, String>> response = controller.orderFallback();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody())
                .containsEntry("service", "order-service")
                .containsEntry("status", "DEGRADED");
    }
}
