package com.chat_socket.utils;

import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.UserEntity;
import java.security.Principal;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class Security {
    private static final String BEARER_PREFIX = "Bearer ";

    public static UserSecurity getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserSecurity currentUser))
            throw new IllegalStateException("Current user is not authenticated.");
        return currentUser;
    }

    /** Token part of an {@code Authorization: Bearer <token>} header, or null when absent/blank/other scheme. */
    public static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) return null;

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isBlank() ? null : token;
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

    public static UserSecurity getUserSecurityFromPrincipal(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserSecurity user) {
            return user;
        }
        return null;
    }
}
