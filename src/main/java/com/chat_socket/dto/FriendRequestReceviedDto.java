package com.chat_socket.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record FriendRequestReceviedDto(
        UUID id,
        UUID toUser,
        AcceptFriendResponse fromUser,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
