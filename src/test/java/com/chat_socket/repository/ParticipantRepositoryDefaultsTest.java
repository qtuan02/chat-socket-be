package com.chat_socket.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.entity.ParticipantEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.enums.ParticipantRole;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParticipantRepositoryDefaultsTest {
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID U = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static ParticipantRepository repositoryReturning(ParticipantEntity participant) {
        ParticipantRepository repository = mock(ParticipantRepository.class);
        when(repository.findActiveParticipant(any(), any())).thenCallRealMethod();
        when(repository.findByIdConversationIdAndIdUserId(C, U)).thenReturn(Optional.ofNullable(participant));
        return repository;
    }

    private static ParticipantEntity participant() {
        ConversationEntity conversation = TestFixtures.conversation(C, ConversationType.GROUP);
        return TestFixtures.participant(conversation, TestFixtures.user(U), ParticipantRole.MEMBER);
    }

    @Test
    void findActiveParticipant_activeParticipant_isReturned() {
        ParticipantEntity participant = participant();

        assertThat(repositoryReturning(participant).findActiveParticipant(C, U)).contains(participant);
    }

    @Test
    void findActiveParticipant_leftParticipant_isEmpty() {
        ParticipantEntity participant = participant();
        participant.setLeftAt(TestFixtures.FIXED_TIME);

        assertThat(repositoryReturning(participant).findActiveParticipant(C, U)).isEmpty();
    }

    @Test
    void findActiveParticipant_deletedParticipant_isEmpty() {
        ParticipantEntity participant = participant();
        participant.setDeletedAt(TestFixtures.FIXED_TIME);

        assertThat(repositoryReturning(participant).findActiveParticipant(C, U)).isEmpty();
    }

    @Test
    void findActiveParticipant_missing_isEmpty() {
        assertThat(repositoryReturning(null).findActiveParticipant(C, U)).isEmpty();
    }
}
