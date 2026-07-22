package com.medha.realtimechatservice.controller;

import com.medha.realtimechatservice.dto.ChatRoomCreateRequest;
import com.medha.realtimechatservice.dto.ChatRoomResponse;
import com.medha.realtimechatservice.dto.RoomParticipantsResponse;
import com.medha.realtimechatservice.service.ChatRoomService;
import com.medha.realtimechatservice.service.PresenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for chat room lifecycle (create/list/lookup) and live presence.
 *
 * <p>Actual message exchange happens over the WebSocket/STOMP endpoint
 * ({@link com.medha.realtimechatservice.websocket.ChatWebSocketController}); this controller
 * only manages room metadata, which naturally belongs in a normal HTTP resource, and lets a
 * client discover rooms before it opens a WebSocket connection.</p>
 */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final PresenceService presenceService;

    @PostMapping
    public ResponseEntity<ChatRoomResponse> createRoom(@Valid @RequestBody ChatRoomCreateRequest request) {
        ChatRoomResponse response = chatRoomService.createRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ChatRoomResponse>> listRooms() {
        return ResponseEntity.ok(chatRoomService.listRooms());
    }

    @GetMapping("/{roomCode}")
    public ResponseEntity<ChatRoomResponse> getRoom(@PathVariable String roomCode) {
        return ResponseEntity.ok(chatRoomService.getRoom(roomCode));
    }

    @GetMapping("/{roomCode}/participants")
    public ResponseEntity<RoomParticipantsResponse> getParticipants(@PathVariable String roomCode) {
        // Validate the room exists so callers get a clean 404 instead of an empty set.
        chatRoomService.findRoomOrThrow(roomCode);
        var users = presenceService.getOnlineUsers(roomCode);
        return ResponseEntity.ok(new RoomParticipantsResponse(roomCode, users.size(), users));
    }
}
