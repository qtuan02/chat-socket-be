package com.chat_socket.socket;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class SocketEventListener {
    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        System.out.println(
                "socket connected: " + event.getMessage().getHeaders().get("simpSessionId"));
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        System.out.println("socket disconnected: " + event.getSessionId());
    }
}
