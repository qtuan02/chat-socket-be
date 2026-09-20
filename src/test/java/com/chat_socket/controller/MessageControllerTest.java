package com.chat_socket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chat_socket.config.GlobalExceptionHandler;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.DirectMessageRequest;
import com.chat_socket.dto.GroupMessageRequest;
import com.chat_socket.dto.UpdateMessageRequest;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.service.MessageService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    MessageService messageService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MessageController(messageService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void sendDirect_validBody_returns201() throws Exception {
        when(messageService.sendDirectMessage(new DirectMessageRequest(ID, "hi", null, null)))
                .thenReturn(new BaseResponse<>(null, "Message sent successfully.", 201));

        mockMvc.perform(post("/v1/message/direct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientId\":\"" + ID + "\",\"content\":\"hi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Message sent successfully."));
    }

    @Test
    void sendDirect_missingRecipientId_returns400Validation() throws Exception {
        mockMvc.perform(post("/v1/message/direct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.recipientId").value("Recipient is required"));
    }

    @Test
    void sendGroup_missingConversationId_returns400Validation() throws Exception {
        mockMvc.perform(post("/v1/message/group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.conversationId").value("Conversation is required"));
    }

    @Test
    void sendGroup_forbiddenException_returns403() throws Exception {
        when(messageService.sendGroupMessage(new GroupMessageRequest(ID, "hi", null, null)))
                .thenThrow(new ForbiddenException("You are not a participant of this conversation."));

        mockMvc.perform(post("/v1/message/group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + ID + "\",\"content\":\"hi\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not a participant of this conversation."));
    }

    @Test
    void sendGroup_isGuardedByMessageGroupPermission() throws Exception {
        PreAuthorize guard = MessageController.class
                .getMethod("sendGroupMessage", GroupMessageRequest.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(guard).isNotNull();
        assertThat(guard.value()).isEqualTo("@messageGroupPermission.canSendGroup(#request.conversationId())");
    }

    @Test
    void sendDirect_isGuardedByMessageDirectPermission() throws Exception {
        PreAuthorize guard = MessageController.class
                .getMethod("sendDirectMessage", DirectMessageRequest.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(guard).isNotNull();
        assertThat(guard.value())
                .isEqualTo("@messageDirectPermission.canSendDirect(T(java.util.List).of(#request.recipientId()))");
    }

    @Test
    void updateMessage_blankContent_returns400() throws Exception {
        mockMvc.perform(patch("/v1/message/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMessage_returns200() throws Exception {
        when(messageService.updateMessage(ID, new UpdateMessageRequest("edited")))
                .thenReturn(new BaseResponse<>(null, "Message updated successfully.", 200));

        mockMvc.perform(patch("/v1/message/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"edited\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteMessage_returns204() throws Exception {
        when(messageService.deleteMessage(ID)).thenReturn(new BaseResponse<>(null, null, 204));

        mockMvc.perform(delete("/v1/message/{id}", ID)).andExpect(status().isNoContent());
    }
}
