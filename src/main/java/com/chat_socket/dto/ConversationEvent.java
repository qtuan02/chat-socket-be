package com.chat_socket.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationEvent(
        String eventType, UUID conversationId, MessageDto lastMessage, Instant lastMessageAt, long unreadCount) {
    public static ConversationEvent updated(
            UUID conversationId, MessageDto lastMessage, Instant lastMessageAt, long unreadCount) {
        return new ConversationEvent("conversation.updated", conversationId, lastMessage, lastMessageAt, unreadCount);
    }

    public static ConversationEvent groupDeleted(UUID conversationId) {
        return new ConversationEvent("group.deleted", conversationId, null, null, 0);
    }
}
