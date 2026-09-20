package com.chat_socket.service;

import java.util.Optional;
import java.util.UUID;

public interface JwtService {
    String generateToken(UUID userId);

    String generateRefreshToken();

    /** User id from a valid access token; empty when the token is expired, tampered with, or malformed. */
    Optional<UUID> verifyAccessToken(String accessToken);
}
