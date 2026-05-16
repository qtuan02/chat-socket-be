package com.chat_socket.controller;

import com.chat_socket.constant.RouteApi;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.ConversationDto;
import com.chat_socket.dto.ConversationRequest;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.service.ConversationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RouteApi.CONVERSATION_API)
public class ConversationController {
    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping()
    public ResponseEntity<BaseResponse<PaginationResponse<ConversationDto>>> getConversations(
            @ModelAttribute PaginationRequest request, @RequestParam(required = false) ConversationType type) {
        BaseResponse<PaginationResponse<ConversationDto>> body = conversationService.getConversations(request, type);
        return ResponseEntity.status(body.status()).body(body);
    }

    @PostMapping()
    @PreAuthorize("@messageDirectPermission.canSendDirect(#request.memberIds())")
    public ResponseEntity<BaseResponse<ConversationDto>> createConversation(
            @Valid @RequestBody ConversationRequest request) {
        BaseResponse<ConversationDto> body = conversationService.createConversation(request);
        return ResponseEntity.status(body.status()).body(body);
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<BaseResponse<PaginationResponse<MessageDto>>> getMessages(
            @PathVariable UUID conversationId, @ModelAttribute PaginationRequest request) {
        BaseResponse<PaginationResponse<MessageDto>> body = conversationService.getMessages(conversationId, request);
        return ResponseEntity.status(body.status()).body(body);
    }

    @PatchMapping("/{conversationId}/seen")
    public ResponseEntity<BaseResponse<Void>> markAsSeen(@PathVariable UUID conversationId) {
        BaseResponse<Void> body = conversationService.markAsSeen(conversationId);
        return ResponseEntity.status(body.status()).body(body);
    }
}
