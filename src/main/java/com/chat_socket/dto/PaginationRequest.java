package com.chat_socket.dto;

public record PaginationRequest(Integer limit, String cursor, Integer offset) {}
