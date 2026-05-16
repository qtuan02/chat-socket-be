package com.chat_socket.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record FriendDto(
        UUID id, String username, String firstName, String lastName, String avatarUrl, LocalDateTime joinedAt) {}
