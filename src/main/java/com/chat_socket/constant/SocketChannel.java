package com.chat_socket.constant;

public interface SocketChannel {
    String APP = "/app";
    String TOPIC = "/topic";
    String QUEUE = "/queue";
    String CONVERSATION = "/conversations";
    String MESSAGE = "/messages";
    String TYPING = "/typing";
    String ONLINE_USERS = "/online-users";
    String CONVERSATION_QUEUE = QUEUE + CONVERSATION;
    String MESSAGE_TOPIC = CONVERSATION + "/%s" + MESSAGE;
    String TYPING_TOPIC = CONVERSATION + "/%s" + TYPING;
    /** {@code @MessageMapping} pattern (relative to {@link #APP}). */
    String TYPING_MAPPING = CONVERSATION + "/{conversationId}" + TYPING;
}
