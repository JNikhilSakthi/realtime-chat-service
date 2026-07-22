package com.medha.realtimechatservice.websocket;

import com.medha.realtimechatservice.domain.MessageType;
import com.medha.realtimechatservice.dto.ChatMessageDto;
import com.medha.realtimechatservice.service.ChatMessageService;
import com.medha.realtimechatservice.service.PresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketEventListenerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private PresenceService presenceService;

    @Mock
    private ChatMessageService chatMessageService;

    private WebSocketEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new WebSocketEventListener(messagingTemplate, presenceService, chatMessageService);
    }

    private SessionDisconnectEvent disconnectEventWithAttributes(String username, String roomCode) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.DISCONNECT);
        accessor.setSessionId("session-1");
        accessor.setSessionAttributes(new java.util.HashMap<>());
        if (username != null) {
            accessor.getSessionAttributes().put(ChatWebSocketController.SESSION_ATTR_USERNAME, username);
        }
        if (roomCode != null) {
            accessor.getSessionAttributes().put(ChatWebSocketController.SESSION_ATTR_ROOM_CODE, roomCode);
        }
        Message<byte[]> message = org.springframework.messaging.support.MessageBuilder
                .withPayload(new byte[0]).setHeaders(accessor).build();
        return new SessionDisconnectEvent(this, message, "session-1", CloseStatus.NORMAL);
    }

    @Test
    void disconnectBroadcastsLeaveEventAndClearsPresenceWhenSessionJoinedARoom() {
        when(presenceService.leave("ROOM01", "alice")).thenReturn(true);
        when(chatMessageService.saveMessage(eq("ROOM01"), any(ChatMessageDto.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        listener.handleSessionDisconnect(disconnectEventWithAttributes("alice", "ROOM01"));

        verify(presenceService).leave("ROOM01", "alice");
        ArgumentCaptor<ChatMessageDto> captor = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/room/ROOM01"), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getType()).isEqualTo(MessageType.LEAVE);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getSender()).isEqualTo("alice");
    }

    @Test
    void disconnectDoesNothingWhenSessionNeverJoinedARoom() {
        listener.handleSessionDisconnect(disconnectEventWithAttributes(null, null));

        verify(presenceService, never()).leave(anyString(), anyString());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void disconnectDoesNotBroadcastWhenUserWasNotTrackedAsPresent() {
        when(presenceService.leave("ROOM01", "alice")).thenReturn(false);

        listener.handleSessionDisconnect(disconnectEventWithAttributes("alice", "ROOM01"));

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}
