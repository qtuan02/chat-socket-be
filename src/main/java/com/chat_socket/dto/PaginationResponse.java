package com.chat_socket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaginationResponse<T>(List<T> messages, String nextCursor, Integer nextOffset) {
    public PaginationResponse(List<T> messages, String nextCursor) {
        this(messages, nextCursor, null);
    }

    public static <T> PaginationResponse<T> offset(List<T> messages, Integer nextOffset) {
        return new PaginationResponse<>(messages, null, nextOffset);
    }
}
