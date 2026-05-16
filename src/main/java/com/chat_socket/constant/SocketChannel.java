package com.chat_socket.constant;

public interface SocketChannel {
    String APP = "/app";
    String TOPIC = "/topic";
    String QUEUE = "/queue";

    String CONVERSATION = "/conversations";
    String MESSAGE = "/messages";

    String CONVERSATION_QUEUE = QUEUE + CONVERSATION;
    String MESSAGE_TOPIC = CONVERSATION + "/%s" + MESSAGE;
}
