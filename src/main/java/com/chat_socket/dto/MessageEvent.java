package com.chat_socket.dto;

public record MessageEvent(String eventType, MessageDto message) {
    public static MessageEvent created(MessageDto message) {
        return new MessageEvent("message.created", message);
    }

    public static MessageEvent updated(MessageDto message) {
        return new MessageEvent("message.updated", message);
    }

    public static MessageEvent deleted(MessageDto message) {
        return new MessageEvent("message.deleted", message);
    }
}
