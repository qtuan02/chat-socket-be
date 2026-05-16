package com.chat_socket.socket;

import java.util.Set;
import java.util.UUID;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

@Controller
public class SocketController {
    private final UserOnlineRegistry userOnlineRegistry;

    SocketController(UserOnlineRegistry userOnlineRegistry) {
        this.userOnlineRegistry = userOnlineRegistry;
    }

    @SubscribeMapping("/online-users")
    public Set<UUID> onlineUsers() {
        return userOnlineRegistry.onlineUserIds();
    }
}
