package com.chat_socket.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConversationSeenEvent(
        String eventType, UUID conversationId, UUID seenByUserId, UUID lastReadMessageId, LocalDateTime lastReadAt) {
    public static ConversationSeenEvent seen(
            UUID conversationId, UUID seenByUserId, UUID lastReadMessageId, LocalDateTime lastReadAt) {
        return new ConversationSeenEvent(
                "conversation.seen", conversationId, seenByUserId, lastReadMessageId, lastReadAt);
    }
}
