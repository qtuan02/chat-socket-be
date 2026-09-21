package com.chat_socket.dto;

import java.util.UUID;

/** The conversation is gone from this user's list: kicked, left, or deleted. */
public record ConversationRemovedEvent(String eventType, UUID conversationId) {
    public static ConversationRemovedEvent of(UUID conversationId) {
        return new ConversationRemovedEvent("conversation.removed", conversationId);
    }
}
