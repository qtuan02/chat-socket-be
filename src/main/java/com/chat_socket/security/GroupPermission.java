package com.chat_socket.security;

import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.entity.ParticipantEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.enums.ParticipantRole;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.repository.ConversationRepository;
import com.chat_socket.repository.ParticipantRepository;
import com.chat_socket.utils.Security;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component("groupPermission")
public class GroupPermission {
    private final ConversationRepository conversationRepository;
    private final ParticipantRepository participantRepository;

    public GroupPermission(ConversationRepository conversationRepository, ParticipantRepository participantRepository) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
    }

    public boolean canManageGroup(UUID conversationId) {
        if (conversationId == null) return true;

        ConversationEntity conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found."));
        if (conversation.getType() != ConversationType.GROUP)
            throw new NotFoundException("Group conversation not found.");

        UserSecurity currentUser = Security.getCurrentUser();
        ParticipantEntity participant = participantRepository
                .findByIdConversationIdAndIdUserId(conversationId, currentUser.id())
                .orElseThrow(() -> new ForbiddenException("You are not a participant of this conversation."));

        if (participant.getLeftAt() != null || participant.getDeletedAt() != null)
            throw new ForbiddenException("You are not a participant of this conversation.");

        if (participant.getRole() != ParticipantRole.ADMIN)
            throw new ForbiddenException("Only admins can manage this group.");

        return true;
    }
}
