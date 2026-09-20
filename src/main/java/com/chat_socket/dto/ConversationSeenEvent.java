package com.chat_socket.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationSeenEvent(
        String eventType, UUID conversationId, UUID seenByUserId, UUID lastReadMessageId, Instant lastReadAt) {
    public static ConversationSeenEvent seen(
            UUID conversationId, UUID seenByUserId, UUID lastReadMessageId, Instant lastReadAt) {
        return new ConversationSeenEvent(
                "conversation.seen", conversationId, seenByUserId, lastReadMessageId, lastReadAt);
    }
}
