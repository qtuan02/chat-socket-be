package com.chat_socket.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The default method is real; only the derived query underneath is mocked. */
class FriendRepositoryDefaultsTest {
    private static final UUID SMALL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BIG = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void existsFriendship_normalizesArgumentOrderBeforeQuerying() {
        FriendRepository repository = mock(FriendRepository.class);
        when(repository.existsFriendship(any(), any())).thenCallRealMethod();
        when(repository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(true);

        assertThat(repository.existsFriendship(BIG, SMALL)).isTrue();
        assertThat(repository.existsFriendship(SMALL, BIG)).isTrue();
    }
}
