package com.chat_socket.controller;

import com.chat_socket.constant.RouteApi;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.DirectMessageRequest;
import com.chat_socket.dto.GroupMessageRequest;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.UpdateMessageRequest;
import com.chat_socket.service.MessageService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RouteApi.MESSAGE_API)
public class MessageController {
    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/direct")
    @PreAuthorize("@messageDirectPermission.canSendDirect(T(java.util.List).of(#request.recipientId()))")
    public ResponseEntity<BaseResponse<MessageDto>> sendDirectMessage(
            @Valid @RequestBody DirectMessageRequest request) {
        BaseResponse<MessageDto> body = messageService.sendDirectMessage(request);
        return ResponseEntity.status(body.status()).body(body);
    }

    @PostMapping("/group")
    @PreAuthorize("@messageGroupPermission.canSendGroup(#request.conversationId())")
    public ResponseEntity<BaseResponse<MessageDto>> sendGroupMessage(@Valid @RequestBody GroupMessageRequest request) {
        BaseResponse<MessageDto> body = messageService.sendGroupMessage(request);
        return ResponseEntity.status(body.status()).body(body);
    }

    @PatchMapping("/{messageId}")
    public ResponseEntity<BaseResponse<MessageDto>> updateMessage(
            @PathVariable UUID messageId, @Valid @RequestBody UpdateMessageRequest request) {
        BaseResponse<MessageDto> body = messageService.updateMessage(messageId, request);
        return ResponseEntity.status(body.status()).body(body);
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<BaseResponse<Void>> deleteMessage(@PathVariable UUID messageId) {
        BaseResponse<Void> body = messageService.deleteMessage(messageId);
        return ResponseEntity.status(body.status()).body(body);
    }
}
