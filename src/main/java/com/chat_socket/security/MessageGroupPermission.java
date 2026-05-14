package com.chat_socket.security;

import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.repository.ConversationRepository;
import com.chat_socket.repository.ParticipantRepository;
import com.chat_socket.utils.Security;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component("messageGroupPermission")
public class MessageGroupPermission {
    private final ConversationRepository conversationRepository;
    private final ParticipantRepository participantRepository;

    public MessageGroupPermission(
            ConversationRepository conversationRepository, ParticipantRepository participantRepository) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
    }

    public boolean canSendGroup(UUID conversationId) {
        if (conversationId == null) return true;

        UserSecurity currentUser = Security.getCurrentUser();
        ConversationEntity conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found."));

        if (conversation.getType() != ConversationType.GROUP)
            throw new NotFoundException("Group conversation not found.");

        if (!participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                conversationId, currentUser.id()))
            throw new ForbiddenException("You are not a participant of this conversation.");

        return true;
    }
}
