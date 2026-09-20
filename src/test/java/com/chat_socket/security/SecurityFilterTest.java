package com.chat_socket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class SecurityFilterTest {
    @Mock
    JwtService jwtService;

    @Mock
    UserRepository userRepository;

    SecurityFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SecurityFilter(new ObjectMapper(), jwtService, userRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    @Test
    void shouldNotFilter_publicPathsAndOptions() {
        assertThat(filter.shouldNotFilter(request("OPTIONS", "/api/v1/user/me")))
                .isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/api/ws"))).isTrue();
        assertThat(filter.shouldNotFilter(request("GET", "/api/health-check"))).isTrue();
        assertThat(filter.shouldNotFilter(request("POST", "/api/v1/auth/sign-in")))
                .isTrue();
    }

    @Test
    void shouldNotFilter_protectedPath_isFalse() {
        assertThat(filter.shouldNotFilter(request("GET", "/api/v1/user/me"))).isFalse();
        assertThat(filter.shouldNotFilter(request("GET", "/api/v1/conversation")))
                .isFalse();
    }

    @Test
    void missingAuthorizationHeader_writes401Json() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("GET", "/api/v1/user/me"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("\"message\":\"Token not found.\"")
                .contains("\"status\":401");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void bearerWithoutToken_writes401() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/user/me");
        request.addHeader("Authorization", "Bearer   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void invalidToken_writes403() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/user/me");
        request.addHeader("Authorization", "Bearer bad");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.verifyAccessToken("bad")).thenThrow(new JwtException("bad"));

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Token expired or invalid.");
    }

    @Test
    void unknownUser_writes404() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = request("GET", "/api/v1/user/me");
        request.addHeader("Authorization", "Bearer good");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtService.verifyAccessToken("good")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("User does not exist.");
    }

    @Test
    void validToken_setsSecurityContextAndContinuesChain() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = request("GET", "/api/v1/user/me");
        request.addHeader("Authorization", "Bearer good");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtService.verifyAccessToken("good")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(TestFixtures.user(userId)));

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        Object principal =
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal).isInstanceOf(UserSecurity.class);
        assertThat(((UserSecurity) principal).id()).isEqualTo(userId);
    }
}
