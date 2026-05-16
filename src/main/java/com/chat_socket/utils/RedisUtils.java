package com.chat_socket.utils;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisUtils {
    private static final RedisScript<Long> REMOVE_SET_MEMBER_AND_CLEANUP_SCRIPT = RedisScript.of("""
            redis.call('SREM', KEYS[1], ARGV[1])
            if redis.call('SCARD', KEYS[1]) == 0 then
                redis.call('DEL', KEYS[1])
                redis.call('SREM', KEYS[2], ARGV[2])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    RedisUtils(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void save(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    public Long add(String key, String... value) {
        return redisTemplate.opsForSet().add(key, value);
    }

    public Long remove(String key, String... value) {
        return redisTemplate.opsForSet().remove(key, (Object[]) value);
    }

    public Set<String> setMembers(String key) {
        Set<String> members = redisTemplate.opsForSet().members(key);
        return members == null ? Set.of() : members;
    }

    public <V> V execute(RedisScript<V> script, List<String> keys, Object... args) {
        return redisTemplate.execute(script, keys, args);
    }
}
