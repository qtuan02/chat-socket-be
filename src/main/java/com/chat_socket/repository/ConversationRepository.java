package com.chat_socket.repository;

import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.enums.ConversationType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {
    @Query("""
            SELECT c
            FROM ConversationEntity c
            WHERE c.type = :type
                AND c.directUserA.id = :userAId
                AND c.directUserB.id = :userBId
            """)
    Optional<ConversationEntity> findDirectConversation(
            @Param("type") ConversationType type, @Param("userAId") UUID userAId, @Param("userBId") UUID userBId);

    @Query("""
            SELECT c.id
            FROM ConversationEntity c
            JOIN c.participants currentParticipant
            WHERE currentParticipant.user.id = :userId
                AND currentParticipant.leftAt IS NULL
                AND currentParticipant.deletedAt IS NULL
                AND (:type IS NULL OR c.type = :type)
            ORDER BY
                COALESCE(c.lastMessageAt, c.updatedAt) DESC,
                c.id DESC
            """)
    List<UUID> findActiveConversationIdsForUser(
            @Param("userId") UUID userId, @Param("type") ConversationType type, Pageable pageable);

    @Query("""
            SELECT c.id
            FROM ConversationEntity c
            JOIN c.participants currentParticipant
            WHERE currentParticipant.user.id = :userId
                AND currentParticipant.leftAt IS NULL
                AND currentParticipant.deletedAt IS NULL
                AND (:type IS NULL OR c.type = :type)
                AND COALESCE(c.lastMessageAt, c.updatedAt) < :cursor
            ORDER BY
                COALESCE(c.lastMessageAt, c.updatedAt) DESC,
                c.id DESC
            """)
    List<UUID> findActiveConversationIdsForUserBeforeCursor(
            @Param("userId") UUID userId,
            @Param("type") ConversationType type,
            @Param("cursor") LocalDateTime cursor,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT c
            FROM ConversationEntity c
            LEFT JOIN FETCH c.participants participant
            LEFT JOIN FETCH participant.user
            LEFT JOIN FETCH c.lastMessage lastMessage
            LEFT JOIN FETCH lastMessage.sender
            LEFT JOIN FETCH c.createdBy
            LEFT JOIN FETCH c.directUserA
            LEFT JOIN FETCH c.directUserB
            WHERE c.id IN :conversationIds
            """)
    List<ConversationEntity> findConversationsWithDetails(@Param("conversationIds") Collection<UUID> conversationIds);
}
