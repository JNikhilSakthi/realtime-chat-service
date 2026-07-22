package com.medha.realtimechatservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Central WebSocket / STOMP configuration.
 *
 * <p>Clients connect to the {@code /ws} endpoint (with SockJS fallback for browsers/proxies
 * that don't support raw WebSocket). Messages sent by clients to destinations prefixed with
 * {@code /app} are routed to {@code @MessageMapping} handlers. The server broadcasts to
 * subscribers of {@code /topic/**} destinations using a simple in-memory broker, which is
 * sufficient for a single-instance demo and keeps the star technology (WebSocket) front and
 * center without pulling in an external broker such as RabbitMQ or ActiveMQ.</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${chat.websocket.allowed-origin-patterns:*}")
    private String[] allowedOriginPatterns;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Simple in-memory broker for topic (broadcast) and queue (point-to-point) destinations.
        registry.enableSimpleBroker("/topic", "/queue");
        // Prefix for messages bound for @MessageMapping-annotated methods.
        registry.setApplicationDestinationPrefixes("/app");
        // Prefix used for user-specific (point-to-point) destinations, e.g. /user/queue/errors.
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // withSockJS() also exposes a raw WebSocket transport at /ws/websocket for clients
        // (native browsers, our integration tests) that don't need the HTTP fallback
        // transports (xhr-streaming, xhr-polling, etc.) that SockJS negotiates.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOriginPatterns)
                .withSockJS();
    }
}
