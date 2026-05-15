package com.chat_socket.socket;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class UserOnlineRegistry {
    private final ConcurrentMap<UUID, Set<String>> userOnlines = new ConcurrentHashMap<>();

    public void markOnline(UUID userId, String sessionId) {
        userOnlines
                .computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
    }

    public void markOffline(UUID userId) {
        userOnlines.remove(userId);
    }

    public Set<UUID> onlineUserIds() {
        return Set.copyOf(userOnlines.keySet());
    }
}
