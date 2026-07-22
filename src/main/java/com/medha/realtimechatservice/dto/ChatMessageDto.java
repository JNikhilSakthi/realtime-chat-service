package com.medha.realtimechatservice.dto;

import com.medha.realtimechatservice.domain.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Wire format for a chat event, used both for inbound STOMP payloads (client -> server) and
 * outbound broadcasts (server -> subscribers). {@code sentAt} is ignored on the way in and
 * populated by the server on the way out.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDto {

    @NotNull(message = "type is required")
    private MessageType type;

    @NotBlank(message = "sender is required")
    @Size(max = 50, message = "sender must be at most 50 characters")
    private String sender;

    @Size(max = 2000, message = "content must be at most 2000 characters")
    private String content;

    /** Populated by the server; ignored if supplied by a client. */
    private Instant sentAt;
}
