package com.chat_socket.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.TypingEvent;
import com.chat_socket.repository.ParticipantRepository;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class SocketControllerTest {
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID U1 = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    UserOnlineRegistry userOnlineRegistry;

    @Mock
    ParticipantRepository participantRepository;

    @Mock
    SocketEmitter socketEmitter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Principal principalOf(UUID userId) {
        TestFixtures.authenticateAs(userId);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void typing_participant_broadcastsToConversationTopic() {
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(C, U1))
                .thenReturn(true);
        SocketController controller = new SocketController(userOnlineRegistry, participantRepository, socketEmitter);

        controller.typing(C, principalOf(U1));

        ArgumentCaptor<TypingEvent> event = ArgumentCaptor.forClass(TypingEvent.class);
        verify(socketEmitter).emit(eq("/conversations/" + C + "/typing"), event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("typing");
        assertThat(event.getValue().conversationId()).isEqualTo(C);
        assertThat(event.getValue().userId()).isEqualTo(U1);
    }

    @Test
    void typing_nonParticipant_isDropped() {
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(C, U1))
                .thenReturn(false);
        SocketController controller = new SocketController(userOnlineRegistry, participantRepository, socketEmitter);

        controller.typing(C, principalOf(U1));

        verify(socketEmitter, never()).emit(any(), any());
    }

    @Test
    void typing_noPrincipal_isDropped() {
        SocketController controller = new SocketController(userOnlineRegistry, participantRepository, socketEmitter);

        controller.typing(C, null);

        verify(socketEmitter, never()).emit(any(), any());
    }
}
