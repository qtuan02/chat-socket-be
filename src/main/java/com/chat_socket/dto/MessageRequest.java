package com.chat_socket.dto;

import com.chat_socket.enums.MessageType;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record MessageRequest(
        UUID recipientId,
        @NotBlank(message = "Content is required") String content,
        String attachmentUrl,
        UUID conversationId,
        MessageType type) {}
