package com.chat_socket.socket;

import com.chat_socket.constant.Redis;
import com.chat_socket.utils.RedisUtils;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class UserOnlineRegistry {
    private final RedisUtils redisUtils;

    UserOnlineRegistry(RedisUtils redisUtils) {
        this.redisUtils = redisUtils;
    }

    public void markOnline(UUID userId, String sessionId) {
        redisUtils.add(Redis.USER_SESSIONS_KEY_PREFIX + userId, sessionId);
        redisUtils.add(Redis.ONLINE_USERS_KEY, userId.toString());
    }

    public void markOffline(UUID userId, String sessionId) {
        redisUtils.execute(
                Redis.REMOVE_SET_MEMBER_AND_CLEANUP_SCRIPT,
                List.of(Redis.USER_SESSIONS_KEY_PREFIX + userId, Redis.ONLINE_USERS_KEY),
                sessionId,
                userId.toString());
    }

    public Set<UUID> onlineUserIds() {
        Set<String> userIds = redisUtils.setMembers(Redis.ONLINE_USERS_KEY);
        if (userIds.isEmpty()) return Set.of();

        return userIds.stream().map(UUID::fromString).collect(Collectors.toUnmodifiableSet());
    }

    public void clearOnlineUsers() {
        redisUtils.delete(Redis.ONLINE_USERS_KEY + "*");
        redisUtils.delete(Redis.USER_SESSIONS_KEY_PREFIX + "*");
    }
}
