package com.chat_socket.dto;

import com.chat_socket.enums.FriendStatus;
import java.time.Instant;
import java.util.UUID;

public record UserSearchDto(
        UUID id,
        String username,
        String firstName,
        String lastName,
        String avatarUrl,
        Instant joinedAt,
        FriendStatus statusFriend,
        UUID requestId) {}
