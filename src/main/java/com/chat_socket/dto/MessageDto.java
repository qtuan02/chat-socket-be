package com.chat_socket.dto;

import com.chat_socket.enums.MessageType;
import java.time.Instant;
import java.util.UUID;

public record MessageDto(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String content,
        String attachmentUrl,
        MessageType type,
        Instant createdAt,
        Instant updatedAt) {}
