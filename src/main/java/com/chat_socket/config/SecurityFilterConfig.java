package com.chat_socket.config;

import com.chat_socket.constant.RouteApi;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.User;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SecurityFilterConfig extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";

    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public SecurityFilterConfig(ObjectMapper objectMapper, JwtService jwtService, UserRepository userRepository) {
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();

        return HttpMethod.OPTIONS.matches(request.getMethod())
                || requestUri.startsWith("/api" + RouteApi.AUTH_API)
                || requestUri.startsWith(RouteApi.AUTH_API);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {
        // Get token from header
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "Access token not found.");
            return;
        }
        String accessToken =
                authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (accessToken.isBlank()) {
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "Access token not found.");
            return;
        }

        // Verify access token and get user id
        UUID userId = getUserIdFromAccessToken(accessToken);
        if (userId == null) {
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "Access token expired or invalid.");
            return;
        }

        // Find user in database
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, HttpStatus.NOT_FOUND, "User does not exist.");
            return;
        }

        // Set user to security context
        setUserToSecurityContext(user);

        filterChain.doFilter(request, response);
    }

    private void writeErrorResponse(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        BaseResponse<Object> baseResponse = new BaseResponse<>(null, message, status.value());
        objectMapper.writeValue(response.getWriter(), baseResponse);
    }

    private UUID getUserIdFromAccessToken(String accessToken) {
        try {
            return jwtService.verifyAccessToken(accessToken);
        } catch (JwtException | IllegalArgumentException exception) {
            return null;
        }
    }

    private void setUserToSecurityContext(User user) {
        UserSecurity userSecurity = new UserSecurity(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getAvatarUrl());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userSecurity, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
