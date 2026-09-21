package com.chat_socket.dto;

import java.util.UUID;

public record TypingEvent(String eventType, UUID conversationId, UUID userId) {
    public static TypingEvent of(UUID conversationId, UUID userId) {
        return new TypingEvent("typing", conversationId, userId);
    }
}
