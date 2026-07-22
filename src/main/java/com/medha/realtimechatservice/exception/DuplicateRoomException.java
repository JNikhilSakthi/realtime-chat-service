package com.medha.realtimechatservice.exception;

public class DuplicateRoomException extends RuntimeException {

    public DuplicateRoomException(String roomCode) {
        super("Chat room already exists: " + roomCode);
    }
}
