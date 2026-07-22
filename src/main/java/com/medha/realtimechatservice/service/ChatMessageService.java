package com.medha.realtimechatservice.service;

import com.medha.realtimechatservice.domain.ChatMessage;
import com.medha.realtimechatservice.domain.ChatRoom;
import com.medha.realtimechatservice.domain.MessageType;
import com.medha.realtimechatservice.dto.ChatMessageDto;
import com.medha.realtimechatservice.dto.PagedMessageResponse;
import com.medha.realtimechatservice.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomService chatRoomService;

    /**
     * Persists a chat event. TYPING events are never persisted -- callers should not invoke
     * this method for them; the WebSocket controller broadcasts typing indicators directly.
     */
    public ChatMessageDto saveMessage(String roomCode, ChatMessageDto dto) {
        if (dto.getType() == MessageType.TYPING) {
            throw new IllegalArgumentException("TYPING events are not persisted");
        }
        ChatRoom room = chatRoomService.findRoomOrThrow(roomCode);

        ChatMessage entity = ChatMessage.builder()
                .room(room)
                .sender(dto.getSender())
                .content(dto.getContent() == null ? "" : dto.getContent())
                .type(dto.getType())
                .build();

        ChatMessage saved = chatMessageRepository.save(entity);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public PagedMessageResponse getHistory(String roomCode, Pageable pageable) {
        ChatRoom room = chatRoomService.findRoomOrThrow(roomCode);
        Page<ChatMessageDto> page = chatMessageRepository
                .findByRoomIdOrderBySentAtDesc(room.getId(), pageable)
                .map(this::toDto);
        return PagedMessageResponse.from(page);
    }

    private ChatMessageDto toDto(ChatMessage message) {
        return ChatMessageDto.builder()
                .type(message.getType())
                .sender(message.getSender())
                .content(message.getContent())
                .sentAt(message.getSentAt())
                .build();
    }
}
