package com.chat_socket.dto;

import com.chat_socket.enums.ParticipantRole;
import java.time.Instant;
import java.util.UUID;

public record ConversationParticipantDto(
        UUID userId,
        String username,
        String firstName,
        String lastName,
        String avatarUrl,
        ParticipantRole role,
        Instant joinedAt,
        UUID lastReadMessageId,
        Instant lastReadAt) {}
