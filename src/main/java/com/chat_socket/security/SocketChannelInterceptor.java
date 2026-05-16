package com.chat_socket.security;

import com.chat_socket.constant.SocketChannel;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.repository.ParticipantRepository;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.JwtService;
import com.chat_socket.utils.Security;
import java.security.Principal;
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
    private static final String CONVERSATION_MESSAGE_DESTINATION_PREFIX =
            SocketChannel.TOPIC + SocketChannel.CONVERSATION + "/";
    private static final String CONVERSATION_MESSAGE_DESTINATION_SUFFIX = SocketChannel.MESSAGE;

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ParticipantRepository participantRepository;

    public SocketChannelInterceptor(
            JwtService jwtService, UserRepository userRepository, ParticipantRepository participantRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.participantRepository = participantRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticateConnect(accessor);
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) authorizeSubscribe(accessor);

        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
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
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        String destination = accessor.getDestination();

        if (destination == null || !isConversationMessageDestination(destination)) return;

        UUID conversationId = conversationIdFromDestination(destination);

        if (principal == null) throw new ForbiddenException("Socket user is not authenticated.");

        UserSecurity userSecurity = Security.getUserSecurityFromPrincipal(principal);
        if (userSecurity == null) throw new ForbiddenException("Socket user is invalid.");

        UUID userId = userSecurity.id();

        if (!participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                conversationId, userId))
            throw new ForbiddenException("You are not a participant of this conversation.");
    }

    private boolean isConversationMessageDestination(String destination) {
        return destination.startsWith(CONVERSATION_MESSAGE_DESTINATION_PREFIX)
                && destination.endsWith(CONVERSATION_MESSAGE_DESTINATION_SUFFIX);
    }

    private UUID conversationIdFromDestination(String destination) {
        String conversationId = destination.substring(
                CONVERSATION_MESSAGE_DESTINATION_PREFIX.length(),
                destination.length() - CONVERSATION_MESSAGE_DESTINATION_SUFFIX.length());
        try {
            return UUID.fromString(conversationId);
        } catch (IllegalArgumentException exception) {
            throw new ForbiddenException("Conversation destination is invalid.");
        }
    }
}
