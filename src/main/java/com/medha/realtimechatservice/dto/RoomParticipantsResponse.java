package com.medha.realtimechatservice.dto;

import java.util.Set;

public record RoomParticipantsResponse(
        String roomCode,
        int count,
        Set<String> usernames
) {
}
