package com.medha.realtimechatservice.repository;

import com.medha.realtimechatservice.domain.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByRoomCode(String roomCode);

    boolean existsByRoomCode(String roomCode);
}
