package com.chat_socket.service;

import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.ConversationDto;
import com.chat_socket.dto.ConversationRequest;
import com.chat_socket.dto.GroupMembersRequest;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.dto.UpdateGroupRequest;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.enums.ConversationType;
import java.util.UUID;

public interface ConversationService {
    BaseResponse<PaginationResponse<ConversationDto>> getConversations(
            PaginationRequest request, ConversationType type);

    BaseResponse<ConversationDto> getConversation(UUID conversationId);

    BaseResponse<ConversationDto> createConversation(ConversationRequest request);

    /** Existing DIRECT conversation between the two users, or a new one with both as participants. */
    ConversationEntity findOrCreateDirectConversation(UUID currentUserId, UUID otherUserId);

    BaseResponse<PaginationResponse<MessageDto>> getMessages(UUID conversationId, PaginationRequest request);

    BaseResponse<Void> markAsSeen(UUID conversationId);

    BaseResponse<Void> deleteGroup(UUID conversationId);

    BaseResponse<ConversationDto> updateGroup(UUID conversationId, UpdateGroupRequest request);

    BaseResponse<ConversationDto> addGroupMembers(UUID conversationId, GroupMembersRequest request);

    BaseResponse<ConversationDto> removeGroupMember(UUID conversationId, UUID memberId);

    BaseResponse<Void> leaveGroup(UUID conversationId);
}
