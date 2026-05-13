package com.chat_socket.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SignUpRequest(
        @NotBlank(message = "Username is required") String username,

        @NotBlank(message = "Email is required") @Email(message = "Email is invalid") String email,

        @NotBlank(message = "Password is required") String password,
        @NotBlank(message = "First name is required") String firstName,
        @NotBlank(message = "Last name is required") String lastName) {}
