package com.chat_socket.socket;

import com.chat_socket.dto.UserSecurity;
import java.security.Principal;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
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
        UserSecurity userSecurity = getUserSecurityFromPrincipal(event.getUser());
        String socketId = event.getMessage().getHeaders().get("simpSessionId").toString();
        userOnlineRegistry.markOnline(userSecurity.id(), socketId);
        socketEmitter.emit("/online-users", userOnlineRegistry.onlineUserIds());
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        UserSecurity userSecurity = getUserSecurityFromPrincipal(event.getUser());
        userOnlineRegistry.markOffline(userSecurity.id());
        socketEmitter.emit("/online-users", userOnlineRegistry.onlineUserIds());
    }

    private UserSecurity getUserSecurityFromPrincipal(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserSecurity user) {
            return user;
        }
        throw new IllegalArgumentException("Invalid principal.");
    }
}
