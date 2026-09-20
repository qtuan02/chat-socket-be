package com.chat_socket.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.UserEntity;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_returnsPrincipalFromContext() {
        UUID userId = UUID.randomUUID();
        UserSecurity expected = TestFixtures.authenticateAs(userId);

        assertThat(Security.getCurrentUser()).isEqualTo(expected);
    }

    @Test
    void getCurrentUser_noAuthentication_throwsIllegalState() {
        assertThatThrownBy(Security::getCurrentUser)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Current user is not authenticated.");
    }

    @Test
    void getUserAuthentication_wrapsUserFieldsIntoUserSecurity() {
        UserEntity user = TestFixtures.user(UUID.randomUUID());
        user.setAvatarUrl("http://avatar");

        UsernamePasswordAuthenticationToken authentication = Security.getUserAuthentication(user);

        assertThat(authentication.getPrincipal())
                .isEqualTo(new UserSecurity(
                        user.getId(), user.getUsername(), "First", "Last", user.getEmail(), "http://avatar"));
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    @Test
    void getUserSecurityFromPrincipal_returnsUserForAuthenticationWithUserSecurityPrincipal() {
        UserEntity user = TestFixtures.user(UUID.randomUUID());
        Principal principal = Security.getUserAuthentication(user);

        UserSecurity result = Security.getUserSecurityFromPrincipal(principal);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(user.getId());
    }

    @Test
    void getUserSecurityFromPrincipal_otherPrincipal_returnsNull() {
        Principal plain = () -> "someone";

        assertThat(Security.getUserSecurityFromPrincipal(plain)).isNull();
        assertThat(Security.getUserSecurityFromPrincipal(null)).isNull();
    }
}
