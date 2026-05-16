package com.chat_socket.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record GroupMembersRequest(
        @NotEmpty(message = "Member ids are required") List<UUID> memberIds) {}
