package com.chat_socket.dto;

import java.util.UUID;

public record UserSummaryDto(UUID id, String username, String firstName, String lastName, String avatarUrl) {}
