package com.chat_socket.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 50, message = "Username must be at most 50 characters") String username,

        @Email(message = "Email is invalid") @Size(max = 255, message = "Email must be at most 255 characters") String email,

        @Size(max = 70, message = "First name must be at most 70 characters") String firstName,

        @Size(max = 30, message = "Last name must be at most 30 characters") String lastName,

        @Size(max = 500, message = "Avatar URL must be at most 500 characters") String avatarUrl,

        @Size(max = 100, message = "Avatar ID must be at most 100 characters") String avatarId,

        String bio,

        @Size(max = 20, message = "Phone must be at most 20 characters") String phone) {}
