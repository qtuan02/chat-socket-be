package com.chat_socket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.ConversationRequest;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.exception.FriendPermissionException;
import com.chat_socket.repository.FriendRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class MessageDirectPermissionTest {
    private static final UUID SMALL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BIG = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock
    FriendRepository friendRepository;

    MessageDirectPermission permission;

    @BeforeEach
    void setUp() {
        TestFixtures.authenticateAs(BIG);
        permission = new MessageDirectPermission(friendRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void canSendDirect_singleRecipient_alwaysTrue() { // deleted in Task 16 together with the no-op @PreAuthorize
        assertThat(permission.canSendDirect(SMALL)).isTrue();
        assertThat(permission.canSendDirect((UUID) null)).isTrue();
    }

    @Test
    void canCreateConversation_nullOrDirect_isAllowedWithoutChecks() {
        assertThat(permission.canCreateConversation(null)).isTrue();
        assertThat(permission.canCreateConversation(
                        new ConversationRequest(ConversationType.DIRECT, null, List.of(SMALL))))
                .isTrue();
    }

    @Test
    void canCreateConversation_group_allFriends_isAllowed() {
        when(friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(true);
        when(friendRepository.existsByUserAIdAndUserBId(BIG, THIRD)).thenReturn(true);

        assertThat(permission.canCreateConversation(
                        new ConversationRequest(ConversationType.GROUP, "g", List.of(SMALL, THIRD))))
                .isTrue();
    }

    @Test
    void canCreateConversation_group_someNotFriends_throwsWithOffenders() {
        when(friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(true);
        when(friendRepository.existsByUserAIdAndUserBId(BIG, THIRD)).thenReturn(false);

        assertThatThrownBy(() -> permission.canCreateConversation(
                        new ConversationRequest(ConversationType.GROUP, "g", List.of(SMALL, THIRD))))
                .isInstanceOfSatisfying(
                        FriendPermissionException.class,
                        ex -> assertThat(ex.getNotFriends()).containsExactly(THIRD));
    }

    @Test
    void canSendDirect_emptyMemberList_throwsIllegalArgument() {
        assertThatThrownBy(() -> permission.canSendDirect(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
