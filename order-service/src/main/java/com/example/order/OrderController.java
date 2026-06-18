package com.example.order;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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
