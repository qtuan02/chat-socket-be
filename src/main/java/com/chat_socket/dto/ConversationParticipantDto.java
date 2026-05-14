package com.chat_socket.dto;

import com.chat_socket.enums.ParticipantRole;
import java.time.LocalDateTime;
import java.util.UUID;

public record ConversationParticipantDto(
        UUID userId,
        String firstName,
        String lastName,
        String avatarUrl,
        ParticipantRole role,
        LocalDateTime joinedAt) {}
