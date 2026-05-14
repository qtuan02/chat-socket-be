package com.chat_socket.dto;

import com.chat_socket.enums.MessageType;
import java.time.LocalDateTime;
import java.util.UUID;

public record MessageDto(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String content,
        String attachmentUrl,
        MessageType type,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
