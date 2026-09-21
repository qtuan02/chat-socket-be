package com.chat_socket.socket;

import com.chat_socket.constant.SocketChannel;
import com.chat_socket.dto.ConversationRemovedEvent;
import com.chat_socket.dto.ConversationSeenEvent;
import com.chat_socket.dto.ConversationUpdatedEvent;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.MessageEvent;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.mapper.ConversationMapper;
import com.chat_socket.repository.MessageRepository;
import com.chat_socket.repository.ParticipantRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Payloads are built immediately (inside the caller's transaction); emits run after commit. */
@Component
public class SocketPublisher {
    private final ParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final ConversationMapper conversationMapper;
    private final SocketEmitter socketEmitter;

    SocketPublisher(
            ParticipantRepository participantRepository,
            MessageRepository messageRepository,
            ConversationMapper conversationMapper,
            SocketEmitter socketEmitter) {
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.conversationMapper = conversationMapper;
        this.socketEmitter = socketEmitter;
    }

    /** A queue payload addressed to one user. */
    private record Delivery(UUID userId, Object event) {}

    public void publishMessageCreatedAfterCommit(ConversationEntity conversation, MessageDto message) {
        String topic = SocketChannel.MESSAGE_TOPIC.formatted(conversation.getId());
        MessageEvent event = MessageEvent.created(message);
        List<Delivery> deliveries = conversationUpdatedDeliveries(conversation);
        publishAfterCommit(() -> {
            socketEmitter.emit(topic, event);
            emitAll(deliveries);
        });
    }

    public void publishMessageUpdatedAfterCommit(UUID conversationId, MessageDto message) {
        String topic = SocketChannel.MESSAGE_TOPIC.formatted(conversationId);
        MessageEvent event = MessageEvent.updated(message);
        publishAfterCommit(() -> socketEmitter.emit(topic, event));
    }

    public void publishMessageDeletedAfterCommit(UUID conversationId, MessageDto message) {
        String topic = SocketChannel.MESSAGE_TOPIC.formatted(conversationId);
        MessageEvent event = MessageEvent.deleted(message);
        publishAfterCommit(() -> socketEmitter.emit(topic, event));
    }

    /** {@code conversation} must have participants (with users) and lastMessage loaded. */
    public void publishConversationUpdatedAfterCommit(ConversationEntity conversation) {
        List<Delivery> deliveries = conversationUpdatedDeliveries(conversation);
        publishAfterCommit(() -> emitAll(deliveries));
    }

    public void publishConversationRemovedAfterCommit(UUID conversationId, Collection<UUID> userIds) {
        ConversationRemovedEvent event = ConversationRemovedEvent.of(conversationId);
        List<Delivery> deliveries =
                userIds.stream().map(userId -> new Delivery(userId, event)).toList();
        publishAfterCommit(() -> emitAll(deliveries));
    }

    public void publishConversationSeenAfterCommit(
            UUID conversationId, UUID seenByUserId, UUID lastReadMessageId, Instant seenAt) {
        ConversationSeenEvent event =
                ConversationSeenEvent.seen(conversationId, seenByUserId, lastReadMessageId, seenAt);
        List<Delivery> deliveries = participantRepository.findActiveUserIdsByConversationId(conversationId).stream()
                .map(userId -> new Delivery(userId, event))
                .toList();
        publishAfterCommit(() -> emitAll(deliveries));
    }

    // ponytail: one unread-count query per participant; batch when groups grow past a few dozen members.
    private List<Delivery> conversationUpdatedDeliveries(ConversationEntity conversation) {
        UUID conversationId = conversation.getId();
        return participantRepository.findActiveUserIdsByConversationId(conversationId).stream()
                .map(userId -> new Delivery(
                        userId,
                        ConversationUpdatedEvent.of(
                                conversationMapper.toDto(conversation, unreadCount(conversationId, userId)))))
                .toList();
    }

    private void emitAll(List<Delivery> deliveries) {
        deliveries.forEach(delivery ->
                socketEmitter.emitTo(SocketChannel.CONVERSATION_QUEUE, delivery.event(), delivery.userId()));
    }

    private void publishAfterCommit(Runnable publish) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new SocketSynchronization(publish));
    }

    private long unreadCount(UUID conversationId, UUID userId) {
        return messageRepository.countUnreadMessagesByConversation(userId, List.of(conversationId)).stream()
                .findFirst()
                .map(MessageRepository.UnreadCountProjection::getUnreadCount)
                .orElse(0L);
    }
}
