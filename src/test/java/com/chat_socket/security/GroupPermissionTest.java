package com.chat_socket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.entity.ParticipantEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.enums.ParticipantRole;
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
class GroupPermissionTest {
    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000c001");

    @Mock
    ConversationRepository conversationRepository;

    @Mock
    ParticipantRepository participantRepository;

    GroupPermission permission;

    @BeforeEach
    void setUp() {
        TestFixtures.authenticateAs(ME);
        permission = new GroupPermission(conversationRepository, participantRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private ParticipantEntity stubGroupWithMe(ParticipantRole role) {
        ConversationEntity conversation = TestFixtures.conversation(C, ConversationType.GROUP);
        ParticipantEntity me = TestFixtures.participant(conversation, TestFixtures.user(ME), role);
        when(conversationRepository.findById(C)).thenReturn(Optional.of(conversation));
        when(participantRepository.findByIdConversationIdAndIdUserId(C, ME)).thenReturn(Optional.of(me));
        return me;
    }

    @Test
    void nullConversationId_isAllowed() {
        assertThat(permission.canManageGroup(null)).isTrue();
    }

    @Test
    void unknownConversation_throwsNotFound() {
        when(conversationRepository.findById(C)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permission.canManageGroup(C)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void directConversation_throwsNotFound() {
        when(conversationRepository.findById(C))
                .thenReturn(Optional.of(TestFixtures.conversation(C, ConversationType.DIRECT)));

        assertThatThrownBy(() -> permission.canManageGroup(C))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Group conversation not found.");
    }

    @Test
    void notParticipant_throwsForbidden() {
        when(conversationRepository.findById(C))
                .thenReturn(Optional.of(TestFixtures.conversation(C, ConversationType.GROUP)));
        when(participantRepository.findByIdConversationIdAndIdUserId(C, ME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permission.canManageGroup(C)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void leftParticipant_throwsForbidden() { // moves to ParticipantRepositoryDefaultsTest in Task 11
        stubGroupWithMe(ParticipantRole.ADMIN).setLeftAt(TestFixtures.FIXED_TIME);

        assertThatThrownBy(() -> permission.canManageGroup(C)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void member_throwsForbidden() {
        stubGroupWithMe(ParticipantRole.MEMBER);

        assertThatThrownBy(() -> permission.canManageGroup(C))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only admins can manage this group.");
    }

    @Test
    void admin_isAllowed() {
        stubGroupWithMe(ParticipantRole.ADMIN);

        assertThat(permission.canManageGroup(C)).isTrue();
    }
}
