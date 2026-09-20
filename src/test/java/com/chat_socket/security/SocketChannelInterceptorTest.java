package com.chat_socket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.repository.ParticipantRepository;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.JwtService;
import com.chat_socket.utils.Security;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class SocketChannelInterceptorTest {
    private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-00000000c001");

    @Mock
    JwtService jwtService;

    @Mock
    UserRepository userRepository;

    @Mock
    ParticipantRepository participantRepository;

    MessageChannel channel = mock(MessageChannel.class);
    SocketChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new SocketChannelInterceptor(jwtService, userRepository, participantRepository);
    }

    /** Builds a STOMP frame whose header accessor stays mutable so the interceptor's setUser() is observable. */
    private static StompHeaderAccessor accessor(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private static Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void connect_withoutAuthorization_throwsNotFound() {
        StompHeaderAccessor accessor = accessor(StompCommand.CONNECT);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Token not found.");
    }

    @Test
    void connect_invalidToken_throwsForbidden() {
        StompHeaderAccessor accessor = accessor(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer bad");
        when(jwtService.verifyAccessToken("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Token expired or invalid.");
    }

    @Test
    void connect_validToken_setsUserOnAccessor() {
        UUID userId = UUID.randomUUID();
        UserEntity user = TestFixtures.user(userId);
        StompHeaderAccessor accessor = accessor(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer good");
        when(jwtService.verifyAccessToken("good")).thenReturn(Optional.of(userId));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        interceptor.preSend(message(accessor), channel);

        assertThat(accessor.getUser()).isInstanceOf(Authentication.class);
        UserSecurity principal = Security.getUserSecurityFromPrincipal(accessor.getUser());
        assertThat(principal).isNotNull();
        assertThat(principal.id()).isEqualTo(userId);
    }

    @Test
    void subscribe_nonConversationDestination_passesThrough() {
        StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/online-users");

        Message<?> result = interceptor.preSend(message(accessor), channel);

        assertThat(result).isNotNull();
    }

    @Test
    void subscribe_conversationMessages_withoutUser_throwsForbidden() {
        StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversations/" + CONVERSATION_ID + "/messages");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Socket user is not authenticated.");
    }

    @Test
    void subscribe_conversationMessages_invalidUuid_throwsForbidden() {
        StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversations/not-a-uuid/messages");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Conversation destination is invalid.");
    }

    @Test
    void subscribe_conversationMessages_notParticipant_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversations/" + CONVERSATION_ID + "/messages");
        accessor.setUser(Security.getUserAuthentication(TestFixtures.user(userId)));
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                        CONVERSATION_ID, userId))
                .thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), channel))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You are not a participant of this conversation.");
    }

    @Test
    void subscribe_conversationMessages_participant_passes() {
        UUID userId = UUID.randomUUID();
        StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversations/" + CONVERSATION_ID + "/messages");
        accessor.setUser(Security.getUserAuthentication(TestFixtures.user(userId)));
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                        CONVERSATION_ID, userId))
                .thenReturn(true);

        Message<?> result = interceptor.preSend(message(accessor), channel);

        assertThat(result).isNotNull();
    }
}
