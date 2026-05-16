package com.chat_socket.repository;

import com.chat_socket.entity.ParticipantEntity;
import com.chat_socket.entity.ParticipantIdEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ParticipantRepository extends JpaRepository<ParticipantEntity, ParticipantIdEntity> {
    boolean existsByIdConversationIdAndIdUserId(UUID conversationId, UUID userId);

    boolean existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(UUID conversationId, UUID userId);

    Optional<ParticipantEntity> findByIdConversationIdAndIdUserId(UUID conversationId, UUID userId);

    @Query("""
            SELECT p.user.id
            FROM ParticipantEntity p
            WHERE p.conversation.id = :conversationId
                AND p.leftAt IS NULL
                AND p.deletedAt IS NULL
            """)
    List<UUID> findActiveUserIdsByConversationId(@Param("conversationId") UUID conversationId);
}
