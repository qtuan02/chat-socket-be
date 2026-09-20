package com.chat_socket.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chat_socket.dto.ConversationEvent;
import com.chat_socket.dto.ConversationSeenEvent;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.enums.MessageType;
import com.chat_socket.repository.MessageRepository;
import com.chat_socket.repository.ParticipantRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class SocketPublisherTest {
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID U1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID U2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LocalDateTime AT = LocalDateTime.of(2026, 1, 1, 12, 0);
    private static final MessageDto MESSAGE =
            new MessageDto(UUID.randomUUID(), C, U1, "hi", null, MessageType.TEXT, AT, AT);

    @Mock
    ParticipantRepository participantRepository;

    @Mock
    SocketEmitter socketEmitter;

    @Mock
    MessageRepository messageRepository;

    SocketPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SocketPublisher(participantRepository, socketEmitter, messageRepository);
    }

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private MessageRepository.UnreadCountProjection unread(long count) {
        MessageRepository.UnreadCountProjection projection = mock(MessageRepository.UnreadCountProjection.class);
        when(projection.getUnreadCount()).thenReturn(count);
        return projection;
    }

    @Test
    void publishMessage_noTransaction_emitsTopicAndPerUserQueueWithUnreadCount() {
        MessageRepository.UnreadCountProjection zero = unread(0);
        MessageRepository.UnreadCountProjection four = unread(4);
        when(participantRepository.findActiveUserIdsByConversationId(C)).thenReturn(List.of(U1, U2));
        when(messageRepository.countUnreadMessagesByConversation(U1, List.of(C)))
                .thenReturn(List.of(zero));
        when(messageRepository.countUnreadMessagesByConversation(U2, List.of(C)))
                .thenReturn(List.of(four));

        publisher.publishMessageAfterCommit(C, MESSAGE, AT);

        verify(socketEmitter).emit("/conversations/" + C + "/messages", MESSAGE);
        ArgumentCaptor<ConversationEvent> events = ArgumentCaptor.forClass(ConversationEvent.class);
        verify(socketEmitter).emitTo(eq("/queue/conversations"), events.capture(), eq(U1));
        verify(socketEmitter).emitTo(eq("/queue/conversations"), events.capture(), eq(U2));
        assertThat(events.getAllValues())
                .extracting(ConversationEvent::eventType, ConversationEvent::unreadCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("conversation.updated", 0L),
                        org.assertj.core.groups.Tuple.tuple("conversation.updated", 4L));
    }

    @Test
    void publishMessage_userWithoutUnreadRow_getsZero() {
        when(participantRepository.findActiveUserIdsByConversationId(C)).thenReturn(List.of(U1));
        when(messageRepository.countUnreadMessagesByConversation(U1, List.of(C)))
                .thenReturn(List.of());

        publisher.publishMessageAfterCommit(C, MESSAGE, AT);

        ArgumentCaptor<ConversationEvent> event = ArgumentCaptor.forClass(ConversationEvent.class);
        verify(socketEmitter).emitTo(eq("/queue/conversations"), event.capture(), eq(U1));
        assertThat(event.getValue().unreadCount()).isZero();
    }

    @Test
    void publishMessage_insideTransaction_defersUntilAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        when(participantRepository.findActiveUserIdsByConversationId(C)).thenReturn(List.of(U1));
        when(messageRepository.countUnreadMessagesByConversation(U1, List.of(C)))
                .thenReturn(List.of());

        publisher.publishMessageAfterCommit(C, MESSAGE, AT);

        verifyNoInteractions(socketEmitter);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.getFirst().afterCommit();
        verify(socketEmitter).emit("/conversations/" + C + "/messages", MESSAGE);
        verify(socketEmitter).emitTo(eq("/queue/conversations"), any(ConversationEvent.class), eq(U1));
    }

    @Test
    void publishConversationUpdated_emitsToEachActiveUserWithZeroUnread() {
        when(participantRepository.findActiveUserIdsByConversationId(C)).thenReturn(List.of(U1, U2));

        publisher.publishConversationUpdatedAfterCommit(C, null, AT);

        ArgumentCaptor<ConversationEvent> event = ArgumentCaptor.forClass(ConversationEvent.class);
        verify(socketEmitter).emitTo(eq("/queue/conversations"), event.capture(), eq(U1));
        verify(socketEmitter).emitTo(eq("/queue/conversations"), any(ConversationEvent.class), eq(U2));
        assertThat(event.getValue().lastMessage()).isNull();
        assertThat(event.getValue().lastMessageAt()).isEqualTo(AT);
        verify(socketEmitter, never()).emit(any(), any());
    }

    @Test
    void publishGroupDeleted_emitsOnlyToTheDeletingUser() {
        publisher.publishGroupDeletedAfterCommit(C, U1);

        ArgumentCaptor<ConversationEvent> event = ArgumentCaptor.forClass(ConversationEvent.class);
        verify(socketEmitter).emitTo(eq("/queue/conversations"), event.capture(), eq(U1));
        assertThat(event.getValue().eventType()).isEqualTo("group.deleted");
        assertThat(event.getValue().conversationId()).isEqualTo(C);
    }

    @Test
    void publishConversationSeen_emitsSeenTopicAndUpdateQueue() {
        publisher.publishConversationSeenAfterCommit(C, U1, MESSAGE, AT, AT.plusMinutes(1));

        ArgumentCaptor<ConversationSeenEvent> seen = ArgumentCaptor.forClass(ConversationSeenEvent.class);
        verify(socketEmitter).emit(eq("/conversations/" + C + "/seen"), seen.capture());
        assertThat(seen.getValue().eventType()).isEqualTo("conversation.seen");
        assertThat(seen.getValue().seenByUserId()).isEqualTo(U1);
        assertThat(seen.getValue().lastReadMessageId()).isEqualTo(MESSAGE.id());
        assertThat(seen.getValue().lastReadAt()).isEqualTo(AT.plusMinutes(1));
        verify(socketEmitter).emitTo(eq("/queue/conversations"), any(ConversationEvent.class), eq(U1));
    }
}
