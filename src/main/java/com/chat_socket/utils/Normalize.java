package com.chat_socket.utils;

import com.chat_socket.dto.UserPair;
import java.util.UUID;

public class Normalize {
    public static UserPair normalizeUserPair(UUID firstUserId, UUID secondUserId) {
        if (firstUserId.toString().compareTo(secondUserId.toString()) <= 0)
            return new UserPair(firstUserId, secondUserId);

        return new UserPair(secondUserId, firstUserId);
    }
}
