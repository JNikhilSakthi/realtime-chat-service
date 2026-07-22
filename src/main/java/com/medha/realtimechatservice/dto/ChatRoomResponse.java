package com.medha.realtimechatservice.dto;

import com.medha.realtimechatservice.domain.ChatRoom;

import java.time.Instant;

public record ChatRoomResponse(
        Long id,
        String roomCode,
        String name,
        String description,
        String status,
        Instant createdAt,
        int onlineUsers
) {

    public static ChatRoomResponse from(ChatRoom room, int onlineUsers) {
        return new ChatRoomResponse(
                room.getId(),
                room.getRoomCode(),
                room.getName(),
                room.getDescription(),
                room.getStatus().name(),
                room.getCreatedAt(),
                onlineUsers
        );
    }
}
