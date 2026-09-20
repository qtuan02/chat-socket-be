package com.chat_socket.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chat_socket.constant.Redis;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class UserOnlineRegistryTest {
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String SESSION_KEY = Redis.USER_SESSIONS_KEY_PREFIX + USER;

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    SetOperations<String, String> setOperations;

    UserOnlineRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new UserOnlineRegistry(redisTemplate);
    }

    @Test
    void markOnline_addsSessionAndUserToSets() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        registry.markOnline(USER, "session-1");

        verify(setOperations).add(SESSION_KEY, "session-1");
        verify(setOperations).add(Redis.ONLINE_USERS_KEY, USER.toString());
    }

    @Test
    void markOffline_runsCleanupScriptWithBothKeys() {
        registry.markOffline(USER, "session-1");

        verify(redisTemplate)
                .execute(
                        Redis.REMOVE_SET_MEMBER_AND_CLEANUP_SCRIPT,
                        List.of(SESSION_KEY, Redis.ONLINE_USERS_KEY),
                        "session-1",
                        USER.toString());
    }

    @Test
    void onlineUserIds_parsesMembers() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(Redis.ONLINE_USERS_KEY)).thenReturn(Set.of(USER.toString()));

        assertThat(registry.onlineUserIds()).containsExactly(USER);
    }

    @Test
    void onlineUserIds_nullOrEmptyMembers_isEmptySet() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(Redis.ONLINE_USERS_KEY)).thenReturn(null);

        assertThat(registry.onlineUserIds()).isEmpty();
    }

    @Test
    void clearOnlineUsers_deletesEverySessionKeyAndTheOnlineSet() {
        Set<String> sessionKeys = Set.of(SESSION_KEY, Redis.USER_SESSIONS_KEY_PREFIX + "other");
        when(redisTemplate.keys(Redis.USER_SESSIONS_KEY_PREFIX + "*")).thenReturn(sessionKeys);

        registry.clearOnlineUsers();

        verify(redisTemplate).delete(sessionKeys);
        verify(redisTemplate).delete(Redis.ONLINE_USERS_KEY);
    }

    @Test
    void clearOnlineUsers_noSessionKeys_onlyDeletesOnlineSet() {
        when(redisTemplate.keys(Redis.USER_SESSIONS_KEY_PREFIX + "*")).thenReturn(Set.of());

        registry.clearOnlineUsers();

        verify(redisTemplate, never()).delete(Set.of());
        verify(redisTemplate).delete(Redis.ONLINE_USERS_KEY);
    }
}
