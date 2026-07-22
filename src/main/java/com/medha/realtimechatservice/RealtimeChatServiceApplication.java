package com.medha.realtimechatservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Real-Time Chat Service.
 *
 * <p>This application demonstrates Spring's WebSocket support using the STOMP sub-protocol
 * layered over SockJS fallback transports. Chat rooms and message history are persisted to
 * MySQL via Spring Data JPA, while live message delivery, presence tracking and typing
 * indicators flow entirely over WebSocket using an in-memory STOMP broker (no external
 * message broker is required for this demo).</p>
 */
@SpringBootApplication
public class RealtimeChatServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealtimeChatServiceApplication.class, args);
    }
}
