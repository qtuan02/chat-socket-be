package com.chat_socket.dto;

import java.time.Instant;
import java.util.UUID;

public record FriendRequestSentDto(
        UUID id, UUID fromUser, AcceptFriendResponse toUser, String message, Instant createdAt, Instant updatedAt) {}
