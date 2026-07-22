package com.medha.realtimechatservice.domain;

/**
 * Kind of event carried by a chat message on the wire and, where noted, persisted to history.
 */
public enum MessageType {
    /** A regular chat message sent by a user. Persisted. */
    CHAT,
    /** Broadcast when a user joins a room. Persisted so history shows join events. */
    JOIN,
    /** Broadcast when a user leaves/disconnects from a room. Persisted. */
    LEAVE,
    /** Ephemeral "user is typing" notification. Never persisted. */
    TYPING
}
