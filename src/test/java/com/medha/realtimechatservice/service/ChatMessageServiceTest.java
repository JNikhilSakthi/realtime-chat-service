package com.medha.realtimechatservice.service;

import com.medha.realtimechatservice.domain.ChatMessage;
import com.medha.realtimechatservice.domain.ChatRoom;
import com.medha.realtimechatservice.domain.MessageType;
import com.medha.realtimechatservice.dto.ChatMessageDto;
import com.medha.realtimechatservice.dto.PagedMessageResponse;
import com.medha.realtimechatservice.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatRoomService chatRoomService;

    private ChatMessageService chatMessageService;

    @BeforeEach
    void setUp() {
        chatMessageService = new ChatMessageService(chatMessageRepository, chatRoomService);
    }

    private ChatRoom sampleRoom() {
        return ChatRoom.builder().id(1L).roomCode("ROOM01").name("General")
                .status(ChatRoom.RoomStatus.ACTIVE).createdAt(Instant.now()).build();
    }

    @Test
    void saveMessagePersistsChatEventAndReturnsDto() {
        ChatRoom room = sampleRoom();
        when(chatRoomService.findRoomOrThrow("ROOM01")).thenReturn(room);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage entity = invocation.getArgument(0);
            entity.setId(10L);
            entity.setSentAt(Instant.now());
            return entity;
        });

        ChatMessageDto input = ChatMessageDto.builder()
                .type(MessageType.CHAT).sender("alice").content("hello").build();

        ChatMessageDto result = chatMessageService.saveMessage("ROOM01", input);

        assertThat(result.getSender()).isEqualTo("alice");
        assertThat(result.getContent()).isEqualTo("hello");
        assertThat(result.getType()).isEqualTo(MessageType.CHAT);
        assertThat(result.getSentAt()).isNotNull();

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());
        assertThat(captor.getValue().getRoom()).isSameAs(room);
    }

    @Test
    void saveMessageRejectsTypingEvents() {
        ChatMessageDto typing = ChatMessageDto.builder()
                .type(MessageType.TYPING).sender("alice").content("").build();

        assertThatThrownBy(() -> chatMessageService.saveMessage("ROOM01", typing))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveMessageDefaultsNullContentToEmptyString() {
        ChatRoom room = sampleRoom();
        when(chatRoomService.findRoomOrThrow("ROOM01")).thenReturn(room);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatMessageDto joinEvent = ChatMessageDto.builder()
                .type(MessageType.JOIN).sender("bob").content(null).build();

        ChatMessageDto result = chatMessageService.saveMessage("ROOM01", joinEvent);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void getHistoryReturnsPagedResponseOrderedNewestFirst() {
        ChatRoom room = sampleRoom();
        when(chatRoomService.findRoomOrThrow("ROOM01")).thenReturn(room);

        ChatMessage msg1 = ChatMessage.builder().id(1L).room(room).sender("alice")
                .content("hi").type(MessageType.CHAT).sentAt(Instant.now()).build();
        Pageable pageable = PageRequest.of(0, 20);
        when(chatMessageRepository.findByRoomIdOrderBySentAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(msg1), pageable, 1));

        PagedMessageResponse response = chatMessageService.getHistory("ROOM01", pageable);

        assertThat(response.messages()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.messages().get(0).getSender()).isEqualTo("alice");
    }
}
