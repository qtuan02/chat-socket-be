package com.chat_socket.security;

import com.chat_socket.entity.UserEntity;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.JwtService;
import com.chat_socket.utils.Security;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class SocketChannelInterceptor implements ChannelInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public SocketChannelInterceptor(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) return message;

        String authorizationHeader = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX))
            throw new NotFoundException("Token not found.");

        String accessToken =
                authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (accessToken.isBlank()) throw new NotFoundException("Token not found.");

        UUID userId = Security.getUserIdFromAccessToken(jwtService, accessToken);
        if (userId == null) throw new ForbiddenException("Token expired or invalid.");

        UserEntity user =
                userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User does not exist."));

        UsernamePasswordAuthenticationToken authentication = Security.getUserAuthentication(user);
        accessor.setUser(authentication);

        return message;
    }
}
