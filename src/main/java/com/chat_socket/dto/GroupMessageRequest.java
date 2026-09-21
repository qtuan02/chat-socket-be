package com.chat_socket.dto;

import com.chat_socket.enums.MessageType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GroupMessageRequest(
        @NotNull(message = "Conversation is required") UUID conversationId,
        String content,
        MessageType type,
        String attachmentUrl) {}
