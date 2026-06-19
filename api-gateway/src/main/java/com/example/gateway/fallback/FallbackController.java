package com.example.gateway.fallback;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * CircuitBreaker가 차단/타임아웃 시 forward 하는 fallback 엔드포인트.
 * 다운스트림 장애를 사용자에게 그대로 노출하지 않고, 의미 있는 기본 응답(503 + DEGRADED)으로 대체한다.
 */
@RestController
public class FallbackController {

    @GetMapping("/fallback/order")
    public ResponseEntity<Map<String, String>> orderFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "service", "order-service",
                        "status", "DEGRADED",
                        "message", "주문 서비스가 일시적으로 응답하지 않아 기본 응답을 반환합니다."
                ));
    }
}
