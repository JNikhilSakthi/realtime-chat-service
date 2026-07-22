package com.medha.realtimechatservice.service;

import com.medha.realtimechatservice.domain.ChatRoom;
import com.medha.realtimechatservice.dto.ChatRoomCreateRequest;
import com.medha.realtimechatservice.dto.ChatRoomResponse;
import com.medha.realtimechatservice.exception.RoomNotFoundException;
import com.medha.realtimechatservice.repository.ChatRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private PresenceService presenceService;

    private ChatRoomService chatRoomService;

    @BeforeEach
    void setUp() {
        chatRoomService = new ChatRoomService(chatRoomRepository, presenceService);
    }

    @Test
    void createRoomGeneratesUniqueCodeAndPersistsRoom() {
        when(chatRoomRepository.existsByRoomCode(anyString())).thenReturn(false);
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> {
            ChatRoom room = invocation.getArgument(0);
            room.setId(1L);
            room.setCreatedAt(Instant.now());
            return room;
        });

        ChatRoomCreateRequest request = new ChatRoomCreateRequest("General", "General discussion");
        ChatRoomResponse response = chatRoomService.createRoom(request);

        assertThat(response.name()).isEqualTo("General");
        assertThat(response.roomCode()).hasSize(6);
        assertThat(response.onlineUsers()).isZero();

        ArgumentCaptor<ChatRoom> captor = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomRepository).save(captor.capture());
        assertThat(captor.getValue().getRoomCode()).isEqualTo(response.roomCode());
        assertThat(captor.getValue().getStatus()).isEqualTo(ChatRoom.RoomStatus.ACTIVE);
    }

    @Test
    void createRoomRetriesWhenGeneratedCodeCollides() {
        // First candidate collides, second is free.
        when(chatRoomRepository.existsByRoomCode(anyString()))
                .thenReturn(true)
                .thenReturn(false);
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> {
            ChatRoom room = invocation.getArgument(0);
            room.setId(2L);
            room.setCreatedAt(Instant.now());
            return room;
        });

        ChatRoomResponse response = chatRoomService.createRoom(new ChatRoomCreateRequest("Retry Room", null));

        assertThat(response.roomCode()).isNotBlank();
        verify(chatRoomRepository, org.mockito.Mockito.atLeast(2)).existsByRoomCode(anyString());
    }

    @Test
    void findRoomOrThrowThrowsWhenRoomMissing() {
        when(chatRoomRepository.findByRoomCode("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatRoomService.findRoomOrThrow("MISSING"))
                .isInstanceOf(RoomNotFoundException.class)
                .hasMessageContaining("MISSING");
    }

    @Test
    void getRoomIncludesOnlineUserCountFromPresenceService() {
        ChatRoom room = ChatRoom.builder()
                .id(5L).roomCode("ABC123").name("Test Room")
                .status(ChatRoom.RoomStatus.ACTIVE).createdAt(Instant.now())
                .build();
        when(chatRoomRepository.findByRoomCode("ABC123")).thenReturn(Optional.of(room));
        when(presenceService.getOnlineCount("ABC123")).thenReturn(3);

        ChatRoomResponse response = chatRoomService.getRoom("ABC123");

        assertThat(response.onlineUsers()).isEqualTo(3);
    }

    @Test
    void listRoomsMapsEachRoomWithItsOwnOnlineCount() {
        ChatRoom room1 = ChatRoom.builder().id(1L).roomCode("R1").name("Room1")
                .status(ChatRoom.RoomStatus.ACTIVE).createdAt(Instant.now()).build();
        ChatRoom room2 = ChatRoom.builder().id(2L).roomCode("R2").name("Room2")
                .status(ChatRoom.RoomStatus.ACTIVE).createdAt(Instant.now()).build();
        when(chatRoomRepository.findAll()).thenReturn(List.of(room1, room2));
        when(presenceService.getOnlineCount("R1")).thenReturn(2);
        when(presenceService.getOnlineCount("R2")).thenReturn(0);

        List<ChatRoomResponse> responses = chatRoomService.listRooms();

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).onlineUsers()).isEqualTo(2);
        assertThat(responses.get(1).onlineUsers()).isZero();
    }
}
