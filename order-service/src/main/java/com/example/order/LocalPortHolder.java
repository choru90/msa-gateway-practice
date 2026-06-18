package com.example.order;

import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * server.port=0 (랜덤 포트) 사용 시, @Value("${server.port}")는 실제 포트가 아니라 "0"을 반환한다.
 * 웹 서버가 실제로 뜬 직후 발생하는 WebServerInitializedEvent에서 진짜 포트를 잡아 보관한다.
 */
@Component
public class LocalPortHolder implements ApplicationListener<WebServerInitializedEvent> {

    private volatile int port;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        this.port = event.getWebServer().getPort();
    }

    public int getPort() {
        return port;
    }
}
