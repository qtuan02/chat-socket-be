package com.chat_socket.service;

import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.ConversationDto;
import com.chat_socket.dto.ConversationRequest;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import java.util.List;
import java.util.UUID;

public interface ConversationService {
    BaseResponse<List<ConversationDto>> getConversations();

    BaseResponse<ConversationDto> createConversation(ConversationRequest request);

    BaseResponse<PaginationResponse<MessageDto>> getMessages(UUID conversationId, PaginationRequest request);
}
