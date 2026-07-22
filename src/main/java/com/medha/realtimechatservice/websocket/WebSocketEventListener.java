package com.medha.realtimechatservice.websocket;

import com.medha.realtimechatservice.domain.MessageType;
import com.medha.realtimechatservice.dto.ChatMessageDto;
import com.medha.realtimechatservice.service.ChatMessageService;
import com.medha.realtimechatservice.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.Map;

/**
 * Reacts to STOMP session lifecycle events so that a user closing their browser tab, losing
 * network connectivity, or clicking away is reflected as a LEAVE broadcast and a presence
 * update -- without the client having to explicitly say goodbye.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;
    private final ChatMessageService chatMessageService;

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return;
        }

        String username = (String) sessionAttributes.get(ChatWebSocketController.SESSION_ATTR_USERNAME);
        String roomCode = (String) sessionAttributes.get(ChatWebSocketController.SESSION_ATTR_ROOM_CODE);
        if (username == null || roomCode == null) {
            return; // session never completed a JOIN, e.g. it only ever hit the info endpoint
        }

        boolean wasPresent = presenceService.leave(roomCode, username);
        if (!wasPresent) {
            return;
        }

        ChatMessageDto leaveEvent = ChatMessageDto.builder()
                .type(MessageType.LEAVE)
                .sender(username)
                .content(username + " left the room")
                .sentAt(Instant.now())
                .build();

        try {
            ChatMessageDto persisted = chatMessageService.saveMessage(roomCode, leaveEvent);
            messagingTemplate.convertAndSend("/topic/room/" + roomCode, persisted);
        } catch (Exception ex) {
            // Room may have been deleted concurrently; still broadcast so subscribers update
            // their participant list even though history isn't persisted for a gone room.
            log.warn("Could not persist LEAVE event for room '{}': {}", roomCode, ex.getMessage());
            messagingTemplate.convertAndSend("/topic/room/" + roomCode, leaveEvent);
        }
        log.info("User '{}' left room '{}'", username, roomCode);
    }
}
