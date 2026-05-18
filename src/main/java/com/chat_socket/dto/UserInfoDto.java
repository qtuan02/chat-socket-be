package com.chat_socket.dto;

import com.chat_socket.enums.FriendStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserInfoDto(
        UUID id,
        String username,
        String firstName,
        String lastName,
        String email,
        String avatarUrl,
        String bio,
        String phone,
        LocalDateTime joinedAt,
        FriendStatus statusFriend) {}
