package com.medha.realtimechatservice.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PagedMessageResponse(
        List<ChatMessageDto> messages,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {

    public static PagedMessageResponse from(Page<ChatMessageDto> page) {
        return new PagedMessageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
