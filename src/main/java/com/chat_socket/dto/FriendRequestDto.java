package com.chat_socket.dto;

import java.time.Instant;
import java.util.UUID;

/** {@code user} is the other side of the request: the recipient on a sent request, the sender on a received one. */
public record FriendRequestDto(UUID id, UserSummaryDto user, String message, Instant createdAt) {}
