package com.example.order;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/order")
public class OrderController {

    private final LocalPortHolder portHolder;

    public OrderController(LocalPortHolder portHolder) {
        this.portHolder = portHolder;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "service", "order-service",
                "port", String.valueOf(portHolder.getPort()),
                "status", "UP"
        );
    }

    /**
     * 데모용 지연 엔드포인트. Gateway의 CircuitBreaker 타임아웃(2초)을 초과하도록 일부러 느리게 응답한다.
     * delayMillis 기본 3000ms → 타임아웃 발생 → Gateway가 /fallback/order 로 대체 응답.
     */
    @GetMapping("/slow")
    public Map<String, String> slow(@RequestParam(defaultValue = "3000") long delayMillis)
            throws InterruptedException {
        Thread.sleep(delayMillis);
        return Map.of(
                "service", "order-service",
                "sleptMillis", String.valueOf(delayMillis),
                "handledByPort", String.valueOf(portHolder.getPort())
        );
    }

    @GetMapping("/{id}")
    public Map<String, String> getOrder(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        // X-User-Id는 Gateway의 JWT 인증 필터가 토큰에서 추출해 넣어주는 헤더 (Task 6)
        return Map.of(
                "orderId", id,
                "handledByPort", String.valueOf(portHolder.getPort()),
                "requestedBy", userId == null ? "unknown" : userId
        );
    }
}
