package com.chat_socket.dto;

/** Full row for the receiving user (their own unreadCount) — the client upserts it into its list. */
public record ConversationUpdatedEvent(String eventType, ConversationDto conversation) {
    public static ConversationUpdatedEvent of(ConversationDto conversation) {
        return new ConversationUpdatedEvent("conversation.updated", conversation);
    }
}
