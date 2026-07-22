package com.medha.realtimechatservice.websocket;

import com.medha.realtimechatservice.domain.MessageType;
import com.medha.realtimechatservice.dto.ChatMessageDto;
import com.medha.realtimechatservice.dto.ChatRoomCreateRequest;
import com.medha.realtimechatservice.dto.ChatRoomResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the WebSocket/STOMP flow: connect over a real WebSocket, join a room,
 * send a chat message, and assert the broadcast round-trip actually happens -- the behavior
 * that matters most for this project, and the one thing plain unit tests cannot verify.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    private TestRestTemplate restTemplate;
    private WebSocketStompClient stompClient;
    private String roomCode;

    @BeforeEach
    void setUp() {
        restTemplate = new TestRestTemplate();
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        // The default ObjectMapper doesn't know java.time types out of the box; register the
        // same JSR-310 module Spring Boot auto-configures for the server side so Instant
        // fields (e.g. ChatMessageDto.sentAt) round-trip correctly in this test client.
        converter.getObjectMapper().findAndRegisterModules();
        stompClient.setMessageConverter(converter);

        ChatRoomCreateRequest createRequest = new ChatRoomCreateRequest("Integration Test Room", "created by test");
        ChatRoomResponse created = restTemplate.postForObject(
                "http://localhost:" + port + "/api/rooms", createRequest, ChatRoomResponse.class);
        roomCode = created.roomCode();
    }

    @Test
    void joiningAndSendingAMessageBroadcastsToSubscribers() throws Exception {
        BlockingQueue<ChatMessageDto> received = new ArrayBlockingQueue<>(10);

        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws/websocket", new WebSocketHttpHeaders(),
                        new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/room/" + roomCode, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ChatMessageDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.offer((ChatMessageDto) payload);
            }
        });

        // The SUBSCRIBE frame above is sent asynchronously; give the broker a brief moment to
        // register it before publishing, so the very first broadcast isn't missed.
        Thread.sleep(300);

        ChatMessageDto joinPayload = ChatMessageDto.builder()
                .type(MessageType.JOIN).sender("alice").content("").build();
        session.send("/app/chat.addUser/" + roomCode, joinPayload);

        ChatMessageDto joinBroadcast = received.poll(5, TimeUnit.SECONDS);
        assertThat(joinBroadcast).isNotNull();
        assertThat(joinBroadcast.getType()).isEqualTo(MessageType.JOIN);
        assertThat(joinBroadcast.getSender()).isEqualTo("alice");

        ChatMessageDto chatPayload = ChatMessageDto.builder()
                .type(MessageType.CHAT).sender("alice").content("hello world").build();
        session.send("/app/chat.sendMessage/" + roomCode, chatPayload);

        ChatMessageDto chatBroadcast = received.poll(5, TimeUnit.SECONDS);
        assertThat(chatBroadcast).isNotNull();
        assertThat(chatBroadcast.getType()).isEqualTo(MessageType.CHAT);
        assertThat(chatBroadcast.getContent()).isEqualTo("hello world");

        session.disconnect();
    }

    @Test
    void typingIndicatorIsBroadcastButNotPersisted() throws Exception {
        BlockingQueue<ChatMessageDto> received = new ArrayBlockingQueue<>(10);

        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws/websocket", new WebSocketHttpHeaders(),
                        new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/room/" + roomCode, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ChatMessageDto.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.offer((ChatMessageDto) payload);
            }
        });

        ChatMessageDto typingPayload = ChatMessageDto.builder()
                .type(MessageType.TYPING).sender("bob").content("").build();
        session.send("/app/chat.typing/" + roomCode, typingPayload);

        ChatMessageDto typingBroadcast = received.poll(5, TimeUnit.SECONDS);
        assertThat(typingBroadcast).isNotNull();
        assertThat(typingBroadcast.getType()).isEqualTo(MessageType.TYPING);

        // History should remain empty: typing events are never persisted.
        var history = restTemplate.getForObject(
                "http://localhost:" + port + "/api/rooms/" + roomCode + "/messages",
                com.medha.realtimechatservice.dto.PagedMessageResponse.class);
        assertThat(history.messages()).isEmpty();

        session.disconnect();
    }
}
