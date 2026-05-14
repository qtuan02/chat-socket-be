package com.chat_socket.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record FriendSendRequest(
        @NotNull(message = "To User is required") UUID toUserId,

        @Size(max = 300, message = "Message must be at most 300 characters") String message) {}
