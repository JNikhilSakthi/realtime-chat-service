package com.medha.realtimechatservice.repository;

import com.medha.realtimechatservice.domain.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findByRoomIdOrderBySentAtDesc(Long roomId, Pageable pageable);

    long countByRoomId(Long roomId);
}
