package com.chat_socket.constant;

public interface SocketChannel {
    String APP = "/app";
    String TOPIC = "/topic";
    String QUEUE = "/queue";

    String CONVERSATION = "/conversations";
    String MESSAGE = "/messages";
    String SEEN = "/seen";

    String CONVERSATION_QUEUE = QUEUE + CONVERSATION;
    String MESSAGE_TOPIC = CONVERSATION + "/%s" + MESSAGE;
    String CONVERSATION_SEEN_TOPIC = CONVERSATION + "/%s" + SEEN;
}
