package com.chat_socket.dto;

import java.util.List;

public record PaginationResponse<T>(List<T> messages, String nextCursor) {}
