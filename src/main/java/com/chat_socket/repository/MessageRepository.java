package com.chat_socket.repository;

import com.chat_socket.entity.MessageEntity;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {
    @Query("""
            SELECT m.conversation.id AS conversationId, COUNT(m.id) AS unreadCount
            FROM MessageEntity m
            JOIN ParticipantEntity p ON p.conversation = m.conversation
            WHERE p.user.id = :userId
                AND m.conversation.id IN :conversationIds
                AND m.deleted = false
                AND m.sender.id <> :userId
                AND (p.lastReadAt IS NULL OR m.createdAt > p.lastReadAt)
            GROUP BY m.conversation.id
            """)
    List<UnreadCountProjection> countUnreadMessagesByConversation(
            @Param("userId") UUID userId, @Param("conversationIds") Collection<UUID> conversationIds);

    @Query("""
            SELECT m
            FROM MessageEntity m
            JOIN FETCH m.conversation c
            JOIN FETCH m.sender
            WHERE c.id = :conversationId
                AND m.deleted = false
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<MessageEntity> findLatestMessages(@Param("conversationId") UUID conversationId, Pageable pageable);

    @Query("""
            SELECT m
            FROM MessageEntity m
            JOIN FETCH m.conversation c
            JOIN FETCH m.sender
            WHERE c.id = :conversationId
                AND m.deleted = false
                AND m.createdAt < :cursor
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    List<MessageEntity> findMessagesBeforeCursor(
            @Param("conversationId") UUID conversationId, @Param("cursor") LocalDateTime cursor, Pageable pageable);

    interface UnreadCountProjection {
        UUID getConversationId();

        long getUnreadCount();
    }
}
