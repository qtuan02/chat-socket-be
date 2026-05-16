package com.chat_socket.security;

import com.chat_socket.constant.RouteApi;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.JwtService;
import com.chat_socket.utils.Security;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
public class SecurityFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";

    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public SecurityFilter(ObjectMapper objectMapper, JwtService jwtService, UserRepository userRepository) {
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();

        return HttpMethod.OPTIONS.matches(request.getMethod())
                || requestUri.matches("/api/ws")
                || requestUri.equals("/api" + RouteApi.HEALTH_API)
                || requestUri.startsWith("/api" + RouteApi.AUTH_API);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {
        // Get token from header
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "Token not found.");
            return;
        }
        String accessToken =
                authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (accessToken.isBlank()) {
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "Token not found.");
            return;
        }

        // Verify Token and get user id
        UUID userId = Security.getUserIdFromAccessToken(jwtService, accessToken);
        if (userId == null) {
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, HttpStatus.FORBIDDEN, "Token expired or invalid.");
            return;
        }

        // Find user in database
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, HttpStatus.NOT_FOUND, "User does not exist.");
            return;
        }

        // Set user to security context
        UsernamePasswordAuthenticationToken authentication = Security.getUserAuthentication(user);
        SecurityContextHolder.getContext().setAuthentication(authentication);

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
}
