package com.medha.realtimechatservice.controller;

import com.medha.realtimechatservice.dto.PagedMessageResponse;
import com.medha.realtimechatservice.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only REST access to persisted chat history, e.g. so a client can load the last page of
 * messages before subscribing to the live WebSocket feed for a room.
 */
@RestController
@RequestMapping("/api/rooms/{roomCode}/messages")
@RequiredArgsConstructor
public class ChatMessageController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ChatMessageService chatMessageService;

    @GetMapping
    public ResponseEntity<PagedMessageResponse> getHistory(
            @PathVariable String roomCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
        return ResponseEntity.ok(chatMessageService.getHistory(roomCode, pageable));
    }
}
