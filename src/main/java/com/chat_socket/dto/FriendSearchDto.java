package com.chat_socket.dto;

import com.chat_socket.enums.FriendSearchStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record FriendSearchDto(
        UUID id,
        String username,
        String firstName,
        String lastName,
        String avatarUrl,
        LocalDateTime joinedAt,
        FriendSearchStatus status,
        UUID requestId) {}
