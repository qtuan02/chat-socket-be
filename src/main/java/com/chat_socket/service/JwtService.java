package com.chat_socket.service;

import com.chat_socket.entity.User;
import java.util.UUID;

public interface JwtService {
    String generateToken(User user);

    String generateRefreshToken();

    UUID verifyAccessToken(String accessToken);
}
