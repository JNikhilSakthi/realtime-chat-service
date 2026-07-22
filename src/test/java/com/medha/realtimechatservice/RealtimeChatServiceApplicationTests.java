package com.medha.realtimechatservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test verifying the full application context (WebSocket config, JPA, controllers,
 * exception handling) wires up correctly against the H2 test database.
 */
@SpringBootTest
class RealtimeChatServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
