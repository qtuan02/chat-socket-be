package com.chat_socket.socket;

import com.chat_socket.constant.SocketChannel;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class SocketEmitter {
    private final SimpMessagingTemplate messagingTemplate;

    SocketEmitter(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void emit(String destination, Object payload) {
        messagingTemplate.convertAndSend(SocketChannel.TOPIC + destination, payload);
    }

    public void emitTo(String destination, Object payload, UUID id) {
        messagingTemplate.convertAndSendToUser(id.toString(), destination, payload);
    }
}
