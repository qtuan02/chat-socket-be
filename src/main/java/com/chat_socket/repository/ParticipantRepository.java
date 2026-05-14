package com.chat_socket.repository;

import com.chat_socket.entity.ParticipantEntity;
import com.chat_socket.entity.ParticipantIdEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParticipantRepository extends JpaRepository<ParticipantEntity, ParticipantIdEntity> {
    boolean existsByIdConversationIdAndIdUserId(UUID conversationId, UUID userId);

    boolean existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(UUID conversationId, UUID userId);

    Optional<ParticipantEntity> findByIdConversationIdAndIdUserId(UUID conversationId, UUID userId);
}
