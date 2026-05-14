package com.chat_socket.dto;

import java.util.UUID;

public record AcceptFriendResponse(UUID id, String firstName, String lastName, String avatarUrl) {}
