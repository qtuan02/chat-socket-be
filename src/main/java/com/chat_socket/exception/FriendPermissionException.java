package com.chat_socket.exception;

import java.util.List;
import java.util.UUID;

public class FriendPermissionException extends RuntimeException {
    private final List<UUID> notFriends;

    public FriendPermissionException(String message, List<UUID> notFriends) {
        super(message);
        this.notFriends = notFriends;
    }

    public List<UUID> getNotFriends() {
        return notFriends;
    }
}
