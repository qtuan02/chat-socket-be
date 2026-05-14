package com.chat_socket.dto;

import com.chat_socket.enums.ConversationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ConversationRequest(
        @NotNull(message = "Type is required") ConversationType type,
        @NotBlank(message = "Name is required") String name,
        @NotEmpty(message = "Member ids are required") List<UUID> memberIds) {}
