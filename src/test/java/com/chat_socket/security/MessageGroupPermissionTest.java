package com.chat_socket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.repository.ConversationRepository;
import com.chat_socket.repository.ParticipantRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class MessageGroupPermissionTest {
    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000c001");

    @Mock
    ConversationRepository conversationRepository;

    @Mock
    ParticipantRepository participantRepository;

    MessageGroupPermission permission;

    @BeforeEach
    void setUp() {
        TestFixtures.authenticateAs(ME);
        permission = new MessageGroupPermission(conversationRepository, participantRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nullConversationId_isAllowed() {
        assertThat(permission.canSendGroup(null)).isTrue();
    }

    @Test
    void unknownConversation_throwsNotFound() {
        when(conversationRepository.findById(C)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permission.canSendGroup(C)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void directConversation_throwsNotFound() {
        when(conversationRepository.findById(C))
                .thenReturn(Optional.of(TestFixtures.conversation(C, ConversationType.DIRECT)));

        assertThatThrownBy(() -> permission.canSendGroup(C))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Group conversation not found.");
    }

    @Test
    void notActiveParticipant_throwsForbidden() {
        when(conversationRepository.findById(C))
                .thenReturn(Optional.of(TestFixtures.conversation(C, ConversationType.GROUP)));
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(C, ME))
                .thenReturn(false);

        assertThatThrownBy(() -> permission.canSendGroup(C)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void activeParticipant_isAllowed() {
        when(conversationRepository.findById(C))
                .thenReturn(Optional.of(TestFixtures.conversation(C, ConversationType.GROUP)));
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(C, ME))
                .thenReturn(true);

        assertThat(permission.canSendGroup(C)).isTrue();
    }
}
