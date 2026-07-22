package com.medha.realtimechatservice.websocket;

import com.medha.realtimechatservice.domain.MessageType;
import com.medha.realtimechatservice.dto.ChatMessageDto;
import com.medha.realtimechatservice.exception.RoomNotFoundException;
import com.medha.realtimechatservice.service.ChatMessageService;
import com.medha.realtimechatservice.service.ChatRoomService;
import com.medha.realtimechatservice.service.PresenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;

/**
 * Handles inbound STOMP frames published by clients under {@code /app/**} and broadcasts the
 * resulting events to everyone subscribed to {@code /topic/room/{roomCode}}.
 *
 * <p>Three destinations are exposed:
 * <ul>
 *   <li>{@code /app/chat.addUser/{roomCode}} -- a user joins a room. Registers presence, stashes
 *       the username/room on the WebSocket session so {@link WebSocketEventListener} can clean
 *       up on disconnect, and persists + broadcasts a JOIN event.</li>
 *   <li>{@code /app/chat.sendMessage/{roomCode}} -- persists and broadcasts a CHAT message.</li>
 *   <li>{@code /app/chat.typing/{roomCode}} -- broadcasts an ephemeral TYPING indicator; never
 *       persisted and not subject to bean validation on content (it's usually blank).</li>
 * </ul>
 * Clients subscribe to {@code /topic/room/{roomCode}} to receive all three event types.</p>
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    public static final String SESSION_ATTR_USERNAME = "chatUsername";
    public static final String SESSION_ATTR_ROOM_CODE = "chatRoomCode";

    private final ChatMessageService chatMessageService;
    private final ChatRoomService chatRoomService;
    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.addUser/{roomCode}")
    public void addUser(@DestinationVariable String roomCode,
                         @Payload @Valid ChatMessageDto message,
                         SimpMessageHeaderAccessor headerAccessor) {

        // Fail fast with a clear error rather than letting the JOIN silently vanish.
        chatRoomService.findRoomOrThrow(roomCode);

        String username = message.getSender();
        if (headerAccessor.getSessionAttributes() != null) {
            headerAccessor.getSessionAttributes().put(SESSION_ATTR_USERNAME, username);
            headerAccessor.getSessionAttributes().put(SESSION_ATTR_ROOM_CODE, roomCode);
        }

        presenceService.join(roomCode, username);

        ChatMessageDto joinEvent = ChatMessageDto.builder()
                .type(MessageType.JOIN)
                .sender(username)
                .content(username + " joined the room")
                .sentAt(Instant.now())
                .build();

        ChatMessageDto persisted = chatMessageService.saveMessage(roomCode, joinEvent);
        messagingTemplate.convertAndSend("/topic/room/" + roomCode, persisted);
        log.info("User '{}' joined room '{}'", username, roomCode);
    }

    @MessageMapping("/chat.sendMessage/{roomCode}")
    public void sendMessage(@DestinationVariable String roomCode,
                             @Payload @Valid ChatMessageDto message) {
        ChatMessageDto chatEvent = ChatMessageDto.builder()
                .type(MessageType.CHAT)
                .sender(message.getSender())
                .content(message.getContent())
                .sentAt(Instant.now())
                .build();

        ChatMessageDto persisted = chatMessageService.saveMessage(roomCode, chatEvent);
        messagingTemplate.convertAndSend("/topic/room/" + roomCode, persisted);
    }

    @MessageMapping("/chat.typing/{roomCode}")
    public void typing(@DestinationVariable String roomCode, @Payload ChatMessageDto message) {
        ChatMessageDto typingEvent = ChatMessageDto.builder()
                .type(MessageType.TYPING)
                .sender(message.getSender())
                .content(message.getContent())
                .sentAt(Instant.now())
                .build();
        // Typing indicators are pure ephemera: broadcast directly, never touch the database.
        messagingTemplate.convertAndSend("/topic/room/" + roomCode, typingEvent);
    }

    /**
     * Translates exceptions thrown from the handlers above into an error frame delivered only
     * to the offending user's private {@code /user/queue/errors} destination, instead of
     * silently dropping the message or crashing the session.
     */
    @MessageExceptionHandler
    public void handleException(Throwable exception, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        log.warn("WebSocket message handling failed: {}", exception.getMessage());
        String destination = principal != null ? "/user/" + principal.getName() + "/queue/errors" : null;
        String errorMessage = exception instanceof RoomNotFoundException
                ? exception.getMessage()
                : "Unable to process message: " + exception.getMessage();

        if (destination != null) {
            messagingTemplate.convertAndSend(destination, errorMessage);
        } else if (headerAccessor.getSessionId() != null) {
            messagingTemplate.convertAndSendToUser(headerAccessor.getSessionId(), "/queue/errors",
                    errorMessage, createHeaders(headerAccessor.getSessionId()));
        }
    }

    private org.springframework.messaging.MessageHeaders createHeaders(String sessionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);
        return accessor.getMessageHeaders();
    }
}
