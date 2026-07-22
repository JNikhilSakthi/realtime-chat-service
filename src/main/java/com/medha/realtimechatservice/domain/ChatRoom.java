package com.medha.realtimechatservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A chat room that users join to exchange messages in real time.
 *
 * <p>{@code roomCode} is the short, human-shareable identifier used everywhere clients refer to
 * a room: the REST API path, the WebSocket subscription destination ({@code /topic/room/{code}})
 * and the {@code @MessageMapping} destinations clients publish to. The surrogate {@code id} is
 * kept as the JPA primary key / foreign key target for {@link ChatMessage}.</p>
 */
@Entity
@Table(name = "chat_room", indexes = {
        @Index(name = "idx_chat_room_code", columnList = "roomCode", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 12)
    private String roomCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RoomStatus status = RoomStatus.ACTIVE;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public enum RoomStatus {
        ACTIVE, ARCHIVED
    }
}
