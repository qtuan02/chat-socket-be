package com.chat_socket.constant;

import org.springframework.data.redis.core.script.RedisScript;

public interface Redis {
    String ONLINE_USERS_KEY = "chat-socket:online-users";
    String USER_SESSIONS_KEY_PREFIX = "chat-socket:online-user-sessions:";

    RedisScript<Long> REMOVE_SET_MEMBER_AND_CLEANUP_SCRIPT = RedisScript.of("""
            redis.call('SREM', KEYS[1], ARGV[1])
            if redis.call('SCARD', KEYS[1]) == 0 then
                redis.call('DEL', KEYS[1])
                redis.call('SREM', KEYS[2], ARGV[2])
            end
            return 1
            """, Long.class);
}
