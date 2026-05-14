package com.chat_socket.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record FriendRequestSentDto(
        UUID id,
        UUID fromUser,
        AcceptFriendResponse toUser,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
