package com.chat_socket.socket;

import com.chat_socket.dto.UserSecurity;
import com.chat_socket.utils.Security;
import java.security.Principal;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class SocketEventListener {
    private final SocketEmitter socketEmitter;
    private final UserOnlineRegistry userOnlineRegistry;

    SocketEventListener(SocketEmitter socketEmitter, UserOnlineRegistry userOnlineRegistry) {
        this.socketEmitter = socketEmitter;
        this.userOnlineRegistry = userOnlineRegistry;
    }

    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        Principal principal = event.getUser();
        UserSecurity userSecurity = Security.getUserSecurityFromPrincipal(principal);
        if (userSecurity == null) return;

        String sessionId = event.getMessage().getHeaders().get("simpSessionId").toString();
        userOnlineRegistry.markOnline(userSecurity.id(), sessionId);
        socketEmitter.emit("/online-users", userOnlineRegistry.onlineUserIds());
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        Principal principal = event.getUser();
        UserSecurity userSecurity = Security.getUserSecurityFromPrincipal(principal);
        if (userSecurity == null) return;

        userOnlineRegistry.markOffline(userSecurity.id(), event.getSessionId());
        socketEmitter.emit("/online-users", userOnlineRegistry.onlineUserIds());
    }
}
