package com.chat_socket.dto;

import java.time.Instant;
import java.util.UUID;

public record FriendRequestReceviedDto(
        UUID id, UUID toUser, AcceptFriendResponse fromUser, String message, Instant createdAt, Instant updatedAt) {}
