package com.chat_socket.utils;

import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.service.JwtService;
import io.jsonwebtoken.JwtException;
import java.util.Collections;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class Security {
    public static UserSecurity getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserSecurity currentUser))
            throw new IllegalStateException("Current user is not authenticated.");
        return currentUser;
    }

    public static UUID getUserIdFromAccessToken(JwtService jwtService, String accessToken) {
        try {
            return jwtService.verifyAccessToken(accessToken);
        } catch (JwtException | IllegalArgumentException exception) {
            return null;
        }
    }

    public static UsernamePasswordAuthenticationToken getUserAuthentication(UserEntity user) {
        UserSecurity userSecurity = new UserSecurity(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getAvatarUrl());
        return new UsernamePasswordAuthenticationToken(userSecurity, null, Collections.emptyList());
    }
}
