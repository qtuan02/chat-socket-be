package com.chat_socket.dto;

import java.time.Instant;
import java.util.UUID;

public record FriendDto(
        UUID id, String username, String firstName, String lastName, String avatarUrl, Instant joinedAt) {}
