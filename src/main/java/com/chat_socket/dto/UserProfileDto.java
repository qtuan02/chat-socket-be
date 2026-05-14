package com.chat_socket.dto;

import java.util.UUID;

public record UserProfileDto(
        UUID id,
        String username,
        String firstName,
        String lastName,
        String email,
        String avatarUrl,
        String bio,
        String phone) {}
