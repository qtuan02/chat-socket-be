package com.chat_socket.utils;

import com.chat_socket.dto.UserSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityContext {
    public static UserSecurity getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserSecurity currentUser))
            throw new IllegalStateException("Current user is not authenticated.");
        return currentUser;
    }
}
