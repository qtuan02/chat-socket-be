package com.chat_socket.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record FriendActionRequest(
        @NotNull(message = "Request Id is required") UUID requestId) {}
