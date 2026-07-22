package com.medha.realtimechatservice.service;

import com.medha.realtimechatservice.domain.ChatRoom;
import com.medha.realtimechatservice.dto.ChatRoomCreateRequest;
import com.medha.realtimechatservice.dto.ChatRoomResponse;
import com.medha.realtimechatservice.exception.DuplicateRoomException;
import com.medha.realtimechatservice.exception.RoomNotFoundException;
import com.medha.realtimechatservice.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatRoomService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ChatRoomRepository chatRoomRepository;
    private final PresenceService presenceService;

    public ChatRoomResponse createRoom(ChatRoomCreateRequest request) {
        String roomCode = generateUniqueRoomCode();
        ChatRoom room = ChatRoom.builder()
                .roomCode(roomCode)
                .name(request.name())
                .description(request.description())
                .status(ChatRoom.RoomStatus.ACTIVE)
                .build();
        ChatRoom saved = chatRoomRepository.save(room);
        return ChatRoomResponse.from(saved, 0);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> listRooms() {
        return chatRoomRepository.findAll().stream()
                .map(room -> ChatRoomResponse.from(room, presenceService.getOnlineCount(room.getRoomCode())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatRoomResponse getRoom(String roomCode) {
        ChatRoom room = findRoomOrThrow(roomCode);
        return ChatRoomResponse.from(room, presenceService.getOnlineCount(roomCode));
    }

    @Transactional(readOnly = true)
    public ChatRoom findRoomOrThrow(String roomCode) {
        return chatRoomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RoomNotFoundException(roomCode));
    }

    @Transactional(readOnly = true)
    public boolean roomExists(String roomCode) {
        return chatRoomRepository.existsByRoomCode(roomCode);
    }

    private String generateUniqueRoomCode() {
        String code;
        int attempts = 0;
        do {
            code = randomCode();
            attempts++;
            if (attempts > 20) {
                throw new DuplicateRoomException("unable to allocate a unique room code after " + attempts + " attempts");
            }
        } while (chatRoomRepository.existsByRoomCode(code));
        return code;
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
