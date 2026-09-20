package com.chat_socket.dto;

import com.chat_socket.enums.ConversationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationDto(
        UUID id,
        ConversationType type,
        String groupName,
        MessageDto lastMessage,
        Instant lastMessageAt,
        long unreadCount,
        List<ConversationParticipantDto> participants) {}
