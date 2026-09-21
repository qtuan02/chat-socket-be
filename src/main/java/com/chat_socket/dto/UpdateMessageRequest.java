package com.chat_socket.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateMessageRequest(
        @NotBlank(message = "Content is required") String content) {}
