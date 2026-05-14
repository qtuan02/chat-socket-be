package com.chat_socket.service;

import java.util.UUID;

public interface JwtService {
    String generateToken(UUID userId);

    String generateRefreshToken();

    UUID verifyAccessToken(String accessToken);
}
