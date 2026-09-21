package com.chat_socket.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserPairTest {
    private static final UUID SMALL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BIG = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void of_isOrderIndependent() {
        assertThat(UserPair.of(SMALL, BIG)).isEqualTo(new UserPair(SMALL, BIG));
        assertThat(UserPair.of(BIG, SMALL)).isEqualTo(new UserPair(SMALL, BIG));
    }

    @Test
    void of_sameIdTwice_keepsBoth() {
        assertThat(UserPair.of(SMALL, SMALL)).isEqualTo(new UserPair(SMALL, SMALL));
    }
}
