package com.chat_socket.socket;

import com.chat_socket.constant.Redis;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserOnlineRegistry {
    private final StringRedisTemplate redisTemplate;

    UserOnlineRegistry(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void markOnline(UUID userId, String sessionId) {
        redisTemplate.opsForSet().add(Redis.USER_SESSIONS_KEY_PREFIX + userId, sessionId);
        redisTemplate.opsForSet().add(Redis.ONLINE_USERS_KEY, userId.toString());
    }

    public void markOffline(UUID userId, String sessionId) {
        redisTemplate.execute(
                Redis.REMOVE_SET_MEMBER_AND_CLEANUP_SCRIPT,
                List.of(Redis.USER_SESSIONS_KEY_PREFIX + userId, Redis.ONLINE_USERS_KEY),
                sessionId,
                userId.toString());
    }

    public Set<UUID> onlineUserIds() {
        Set<String> userIds = redisTemplate.opsForSet().members(Redis.ONLINE_USERS_KEY);
        if (userIds == null || userIds.isEmpty()) return Set.of();

        return userIds.stream().map(UUID::fromString).collect(Collectors.toUnmodifiableSet());
    }

    /** Runs once at startup: every socket session died with the previous process. */
    public void clearOnlineUsers() {
        // ponytail: KEYS scans the whole keyspace; fine once at boot, switch to SCAN if the key count grows large.
        Set<String> sessionKeys = redisTemplate.keys(Redis.USER_SESSIONS_KEY_PREFIX + "*");
        if (sessionKeys != null && !sessionKeys.isEmpty()) redisTemplate.delete(sessionKeys);
        redisTemplate.delete(Redis.ONLINE_USERS_KEY);
    }
}
