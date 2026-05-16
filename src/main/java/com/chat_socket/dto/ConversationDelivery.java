package com.chat_socket.dto;

import java.util.UUID;

public record ConversationDelivery(UUID userId, ConversationEvent event) {}
