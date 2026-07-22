package com.medha.realtimechatservice.exception;

public class RoomNotFoundException extends RuntimeException {

    public RoomNotFoundException(String roomCode) {
        super("Chat room not found: " + roomCode);
    }
}
