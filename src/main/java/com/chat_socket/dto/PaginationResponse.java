package com.chat_socket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaginationResponse<T>(List<T> items, String nextCursor, Integer nextOffset) {
    public PaginationResponse(List<T> items, String nextCursor) {
        this(items, nextCursor, null);
    }

    public static <T> PaginationResponse<T> offset(List<T> items, Integer nextOffset) {
        return new PaginationResponse<>(items, null, nextOffset);
    }
}
