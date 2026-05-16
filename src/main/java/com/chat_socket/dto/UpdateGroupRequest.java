package com.chat_socket.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateGroupRequest(
        @NotBlank(message = "Name is required") String name) {}
