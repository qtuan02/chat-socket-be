package com.chat_socket.repository;

import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.enums.ConversationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
            SELECT DISTINCT c
            FROM ConversationEntity c
            JOIN c.participants currentParticipant
            LEFT JOIN FETCH c.participants participant
            LEFT JOIN FETCH participant.user
            LEFT JOIN FETCH c.lastMessage lastMessage
            LEFT JOIN FETCH lastMessage.sender
            LEFT JOIN FETCH c.createdBy
            LEFT JOIN FETCH c.directUserA
            LEFT JOIN FETCH c.directUserB
            WHERE currentParticipant.user.id = :userId
                AND currentParticipant.leftAt IS NULL
                AND currentParticipant.deletedAt IS NULL
            ORDER BY
                c.lastMessageAt DESC NULLS LAST,
                c.updatedAt DESC
            """)
    List<ConversationEntity> findActiveConversationsForUser(@Param("userId") UUID userId);
}
