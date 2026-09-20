package com.chat_socket.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.ConversationDto;
import com.chat_socket.dto.ConversationRemovedEvent;
import com.chat_socket.dto.ConversationSeenEvent;
import com.chat_socket.dto.ConversationUpdatedEvent;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.MessageEvent;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.enums.MessageType;
import com.chat_socket.mapper.ConversationMapper;
import com.chat_socket.repository.MessageRepository;
import com.chat_socket.repository.ParticipantRepository;
import java.time.Instant;
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
    private static final Instant AT = Instant.parse("2026-01-01T12:00:00Z");
    private static final MessageDto MESSAGE =
            new MessageDto(UUID.randomUUID(), C, U1, "hi", null, MessageType.TEXT, AT, AT);

    @Mock
    ParticipantRepository participantRepository;

    @Mock
    MessageRepository messageRepository;

    @Mock
    ConversationMapper conversationMapper;

    @Mock
    SocketEmitter socketEmitter;

    ConversationEntity conversation;
    SocketPublisher publisher;

    @BeforeEach
    void setUp() {
        conversation = TestFixtures.conversation(C, ConversationType.GROUP);
        publisher = new SocketPublisher(participantRepository, messageRepository, conversationMapper, socketEmitter);
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

    private ConversationDto dto(long unread) {
        return new ConversationDto(C, ConversationType.GROUP, "g", null, AT, unread, List.of());
    }

    @Test
    void publishMessageCreated_emitsTopicEventAndPerUserConversationWithOwnUnread() {
        MessageRepository.UnreadCountProjection zero = unread(0);
        MessageRepository.UnreadCountProjection four = unread(4);
        when(participantRepository.findActiveUserIdsByConversationId(C)).thenReturn(List.of(U1, U2));
        when(messageRepository.countUnreadMessagesByConversation(U1, List.of(C)))
                .thenReturn(List.of(zero));
        when(messageRepository.countUnreadMessagesByConversation(U2, List.of(C)))
                .thenReturn(List.of(four));
        when(conversationMapper.toDto(conversation, 0L)).thenReturn(dto(0));
        when(conversationMapper.toDto(conversation, 4L)).thenReturn(dto(4));

        publisher.publishMessageCreatedAfterCommit(conversation, MESSAGE);

        ArgumentCaptor<MessageEvent> topic = ArgumentCaptor.forClass(MessageEvent.class);
        verify(socketEmitter).emit(eq("/conversations/" + C + "/messages"), topic.capture());
        assertThat(topic.getValue().eventType()).isEqualTo("message.created");
        assertThat(topic.getValue().message()).isEqualTo(MESSAGE);

        ArgumentCaptor<ConversationUpdatedEvent> events = ArgumentCaptor.forClass(ConversationUpdatedEvent.class);
        verify(socketEmitter).emitTo(eq("/queue/conversations"), events.capture(), eq(U1));
        verify(socketEmitter).emitTo(eq("/queue/conversations"), events.capture(), eq(U2));
        assertThat(events.getAllValues())
                .extracting(e -> e.eventType(), e -> e.conversation().unreadCount())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("conversation.updated", 0L),
                        org.assertj.core.groups.Tuple.tuple("conversation.updated", 4L));
    }

    @Test
    void publishMessageCreated_userWithoutUnreadRow_getsZero() {
        when(participantRepository.findActiveUserIdsByConversationId(C)).thenReturn(List.of(U1));
        when(messageRepository.countUnreadMessagesByConversation(U1, List.of(C)))
                .thenReturn(List.of());
        when(conversationMapper.toDto(conversation, 0L)).thenReturn(dto(0));

        publisher.publishMessageCreatedAfterCommit(conversation, MESSAGE);

        ArgumentCaptor<ConversationUpdatedEvent> event = ArgumentCaptor.forClass(ConversationUpdatedEvent.class);
        verify(socketEmitter).emitTo(eq("/queue/conversations"), event.capture(), eq(U1));
        assertThat(event.getValue().conversation().unreadCount()).isZero();
    }

    @Test
    void publishMessageCreated_insideTransaction_buildsNowEmitsAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        when(participantRepository.findActiveUserIdsByConversationId(C)).thenReturn(List.of(U1));
        when(messageRepository.countUnreadMessagesByConversation(U1, List.of(C)))
                .thenReturn(List.of());
        when(conversationMapper.toDto(conversation, 0L)).thenReturn(dto(0));

        publisher.publishMessageCreatedAfterCommit(conversation, MESSAGE);

        verify(conversationMapper).toDto(conversation, 0L);
        verifyNoInteractions(socketEmitter);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.getFirst().afterCommit();
        verify(socketEmitter).emit(eq("/conversations/" + C + "/messages"), any(MessageEvent.class));
        verify(socketEmitter).emitTo(eq("/queue/conversations"), any(ConversationUpdatedEvent.class), eq(U1));
    }

    @Test
    void publishMessageUpdatedAndDeleted_emitTopicOnly() {
        publisher.publishMessageUpdatedAfterCommit(C, MESSAGE);
        publisher.publishMessageDeletedAfterCommit(C, MESSAGE);

        ArgumentCaptor<MessageEvent> events = ArgumentCaptor.forClass(MessageEvent.class);
        verify(socketEmitter, org.mockito.Mockito.times(2))
                .emit(eq("/conversations/" + C + "/messages"), events.capture());
        assertThat(events.getAllValues())
                .extracting(MessageEvent::eventType)
                .containsExactly("message.updated", "message.deleted");
        verify(socketEmitter, never()).emitTo(any(), any(), any());
    }

    @Test
    void publishConversationUpdated_emitsFullDtoToEachActiveUser() {
        when(participantRepository.findActiveUserIdsByConversationId(C)).thenReturn(List.of(U1, U2));
        when(messageRepository.countUnreadMessagesByConversation(any(), eq(List.of(C))))
                .thenReturn(List.of());
        when(conversationMapper.toDto(conversation, 0L)).thenReturn(dto(0));

        publisher.publishConversationUpdatedAfterCommit(conversation);

        verify(socketEmitter).emitTo(eq("/queue/conversations"), any(ConversationUpdatedEvent.class), eq(U1));
        verify(socketEmitter).emitTo(eq("/queue/conversations"), any(ConversationUpdatedEvent.class), eq(U2));
        verify(socketEmitter, never()).emit(any(), any());
    }

    @Test
    void publishConversationRemoved_emitsOnlyToGivenUsers() {
        publisher.publishConversationRemovedAfterCommit(C, List.of(U2));

        ArgumentCaptor<ConversationRemovedEvent> event = ArgumentCaptor.forClass(ConversationRemovedEvent.class);
        verify(socketEmitter).emitTo(eq("/queue/conversations"), event.capture(), eq(U2));
        verify(socketEmitter, never()).emitTo(any(), any(), eq(U1));
        assertThat(event.getValue().eventType()).isEqualTo("conversation.removed");
        assertThat(event.getValue().conversationId()).isEqualTo(C);
        verifyNoInteractions(participantRepository);
    }

    @Test
    void publishConversationSeen_emitsToEveryActiveUserIncludingSeer() {
        when(participantRepository.findActiveUserIdsByConversationId(C)).thenReturn(List.of(U1, U2));

        publisher.publishConversationSeenAfterCommit(C, U1, MESSAGE.id(), AT.plusSeconds(60));

        ArgumentCaptor<ConversationSeenEvent> seen = ArgumentCaptor.forClass(ConversationSeenEvent.class);
        verify(socketEmitter).emitTo(eq("/queue/conversations"), seen.capture(), eq(U1));
        verify(socketEmitter).emitTo(eq("/queue/conversations"), any(ConversationSeenEvent.class), eq(U2));
        assertThat(seen.getValue().eventType()).isEqualTo("conversation.seen");
        assertThat(seen.getValue().seenByUserId()).isEqualTo(U1);
        assertThat(seen.getValue().lastReadMessageId()).isEqualTo(MESSAGE.id());
        assertThat(seen.getValue().lastReadAt()).isEqualTo(AT.plusSeconds(60));
        verify(socketEmitter, never()).emit(any(), any());
    }
}
