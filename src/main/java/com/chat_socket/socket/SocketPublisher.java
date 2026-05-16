package com.chat_socket.socket;

import com.chat_socket.constant.SocketChannel;
import com.chat_socket.dto.ConversationDelivery;
import com.chat_socket.dto.ConversationEvent;
import com.chat_socket.dto.ConversationSeenEvent;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.repository.MessageRepository;
import com.chat_socket.repository.ParticipantRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class SocketPublisher {

    private final ParticipantRepository participantRepository;
    private final SocketEmitter socketEmitter;
    private final MessageRepository messageRepository;

    SocketPublisher(
            ParticipantRepository participantRepository,
            SocketEmitter socketEmitter,
            MessageRepository messageRepository) {
        this.participantRepository = participantRepository;
        this.socketEmitter = socketEmitter;
        this.messageRepository = messageRepository;
    }

    public void publishMessageAfterCommit(UUID conversationId, MessageDto messageDto, LocalDateTime lastMessageAt) {
        List<ConversationDelivery> updateDeliveries =
                participantRepository.findActiveUserIdsByConversationId(conversationId).stream()
                        .map(userId -> new ConversationDelivery(
                                userId,
                                ConversationEvent.updated(
                                        conversationId,
                                        messageDto,
                                        lastMessageAt,
                                        unreadCount(conversationId, userId))))
                        .toList();

        Runnable publish = () -> {
            socketEmitter.emit(SocketChannel.MESSAGE_TOPIC.formatted(conversationId), messageDto);
            updateDeliveries.forEach(delivery ->
                    socketEmitter.emitTo(SocketChannel.CONVERSATION_QUEUE, delivery.event(), delivery.userId()));
        };

        publishAfterCommit(publish);
    }

    public void publishConversationUpdatedAfterCommit(
            UUID conversationId, MessageDto lastMessage, LocalDateTime lastMessageAt) {
        List<ConversationDelivery> updateDeliveries =
                participantRepository.findActiveUserIdsByConversationId(conversationId).stream()
                        .map(userId -> new ConversationDelivery(
                                userId, ConversationEvent.updated(conversationId, lastMessage, lastMessageAt, 0)))
                        .toList();

        Runnable publish = () -> updateDeliveries.forEach(delivery ->
                socketEmitter.emitTo(SocketChannel.CONVERSATION_QUEUE, delivery.event(), delivery.userId()));

        publishAfterCommit(publish);
    }

    public void publishConversationSeenAfterCommit(
            UUID conversationId,
            UUID userId,
            MessageDto lastMessage,
            LocalDateTime lastMessageAt,
            LocalDateTime seenAt) {
        ConversationEvent event = ConversationEvent.updated(conversationId, lastMessage, lastMessageAt, 0);
        ConversationSeenEvent seenEvent = ConversationSeenEvent.seen(conversationId, userId, lastMessage.id(), seenAt);

        Runnable publish = () -> {
            socketEmitter.emit(SocketChannel.CONVERSATION_SEEN_TOPIC.formatted(conversationId), seenEvent);
            socketEmitter.emitTo(SocketChannel.CONVERSATION_QUEUE, event, userId);
        };

        publishAfterCommit(publish);
    }

    private void publishAfterCommit(Runnable publish) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish.run();
            return;
        }

        SocketSynchronization synchronization = new SocketSynchronization(publish);
        TransactionSynchronizationManager.registerSynchronization(synchronization);
    }

    private long unreadCount(UUID conversationId, UUID userId) {
        return messageRepository.countUnreadMessagesByConversation(userId, List.of(conversationId)).stream()
                .findFirst()
                .map(MessageRepository.UnreadCountProjection::getUnreadCount)
                .orElse(0L);
    }
}
