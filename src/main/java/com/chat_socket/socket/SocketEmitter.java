package com.chat_socket.socket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class SocketEmitter {
    private final SimpMessagingTemplate messagingTemplate;

    public SocketEmitter(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void emit(String destination, Object payload) {
        messagingTemplate.convertAndSend("/topic" + destination, payload);
    }
}
