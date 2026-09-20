package com.chat_socket.dto;

import java.util.UUID;

/** Two user ids in canonical order (smaller {@code toString()} first) so (a,b) and (b,a) look up the same row. */
public record UserPair(UUID userAId, UUID userBId) {
    public static UserPair of(UUID first, UUID second) {
        return first.toString().compareTo(second.toString()) <= 0
                ? new UserPair(first, second)
                : new UserPair(second, first);
    }
}
