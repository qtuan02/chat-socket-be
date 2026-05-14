package com.chat_socket.dto;

import java.util.UUID;

public record UserDto(UUID id, String firstName, String lastName, String avatarUrl) {}
