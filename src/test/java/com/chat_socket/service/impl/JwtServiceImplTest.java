package com.chat_socket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chat_socket.ApplicationYaml;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceImplTest {
    // HS256 needs >= 32 bytes; 64 hex chars = 64 bytes as UTF-8
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private final JwtServiceImpl service = new JwtServiceImpl(new ApplicationYaml(SECRET, 15, 14, List.of()));

    @Test
    void generateToken_thenVerify_returnsSameUserId() {
        UUID userId = UUID.randomUUID();

        String token = service.generateToken(userId);

        assertThat(service.verifyAccessToken(token)).isEqualTo(userId);
    }

    @Test
    void verifyAccessToken_tamperedToken_throwsJwtException() {
        String token = service.generateToken(UUID.randomUUID());
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> service.verifyAccessToken(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void verifyAccessToken_garbage_throws() {
        assertThatThrownBy(() -> service.verifyAccessToken("not-a-jwt"))
                .isInstanceOfAny(JwtException.class, IllegalArgumentException.class);
    }

    @Test
    void generateRefreshToken_is128HexCharsAndRandom() {
        String first = service.generateRefreshToken();
        String second = service.generateRefreshToken();

        assertThat(first).hasSize(128).matches("[0-9a-f]+");
        assertThat(first).isNotEqualTo(second);
    }
}
