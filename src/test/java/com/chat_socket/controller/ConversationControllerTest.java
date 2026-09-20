package com.chat_socket.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chat_socket.config.GlobalExceptionHandler;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.ConversationDto;
import com.chat_socket.dto.ConversationRequest;
import com.chat_socket.dto.GroupMembersRequest;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.dto.UpdateGroupRequest;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.exception.BadRequestException;
import com.chat_socket.exception.FriendPermissionException;
import com.chat_socket.service.ConversationService;
import java.util.List;
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
class ConversationControllerTest {
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID M = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    ConversationService conversationService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(conversationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static String preAuthorizeOf(String method, Class<?>... params) throws NoSuchMethodException {
        PreAuthorize guard =
                ConversationController.class.getMethod(method, params).getAnnotation(PreAuthorize.class);
        return guard == null ? null : guard.value();
    }

    @Test
    void getConversations_bindsCursorLimitAndType() throws Exception {
        when(conversationService.getConversations(
                        new PaginationRequest(10, "2026-01-01T00:00:00", null), ConversationType.GROUP))
                .thenReturn(new BaseResponse<>(new PaginationResponse<>(List.of(), null), "ok", 200));

        mockMvc.perform(get("/v1/conversation")
                        .param("limit", "10")
                        .param("cursor", "2026-01-01T00:00:00")
                        .param("type", "GROUP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void getConversation_returnsDto() throws Exception {
        when(conversationService.getConversation(C))
                .thenReturn(new BaseResponse<>(
                        new ConversationDto(C, ConversationType.GROUP, "Team", null, null, 2, List.of()), "ok", 200));

        mockMvc.perform(get("/v1/conversation/{id}", C))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupName").value("Team"))
                .andExpect(jsonPath("$.data.unreadCount").value(2));
    }

    @Test
    void createConversation_validBody_returns201() throws Exception {
        when(conversationService.createConversation(
                        new ConversationRequest(ConversationType.GROUP, "Team", List.of(M))))
                .thenReturn(new BaseResponse<>(null, "Conversation created successfully.", 201));

        mockMvc.perform(post("/v1/conversation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GROUP\",\"name\":\"Team\",\"memberIds\":[\"" + M + "\"]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void createConversation_missingFields_returns400Validation() throws Exception {
        mockMvc.perform(post("/v1/conversation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.type").value("Type is required"))
                .andExpect(jsonPath("$.data.name").value("Name is required"))
                .andExpect(jsonPath("$.data.memberIds").value("Member ids are required"));
    }

    @Test
    void createConversation_friendPermissionException_returns403WithNotFriends() throws Exception {
        when(conversationService.createConversation(
                        new ConversationRequest(ConversationType.GROUP, "Team", List.of(M))))
                .thenThrow(new FriendPermissionException("You can only add friends to a group.", List.of(M)));

        mockMvc.perform(post("/v1/conversation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"GROUP\",\"name\":\"Team\",\"memberIds\":[\"" + M + "\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.notFriends[0]").value(M.toString()));
    }

    @Test
    void updateGroup_badRequestException_returns400() throws Exception {
        when(conversationService.updateGroup(C, new UpdateGroupRequest("x")))
                .thenThrow(new BadRequestException("Group name is required."));

        mockMvc.perform(patch("/v1/conversation/{id}", C)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Group name is required."));
    }

    @Test
    void getMessages_bindsPathAndPagination() throws Exception {
        when(conversationService.getMessages(C, new PaginationRequest(null, null, null)))
                .thenReturn(new BaseResponse<>(new PaginationResponse<>(List.of(), "next"), "ok", 200));

        mockMvc.perform(get("/v1/conversation/{id}/messages", C))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextCursor").value("next"));
    }

    @Test
    void markAsSeen_deleteGroup_leaveGroup_returnServiceStatus() throws Exception {
        when(conversationService.markAsSeen(C)).thenReturn(new BaseResponse<>(null, "seen", 200));
        when(conversationService.deleteGroup(C)).thenReturn(new BaseResponse<>(null, "deleted", 200));
        when(conversationService.leaveGroup(C)).thenReturn(new BaseResponse<>(null, "left", 200));

        mockMvc.perform(patch("/v1/conversation/{id}/seen", C))
                .andExpect(jsonPath("$.message").value("seen"));
        mockMvc.perform(delete("/v1/conversation/{id}", C))
                .andExpect(jsonPath("$.message").value("deleted"));
        mockMvc.perform(post("/v1/conversation/{id}/leave", C))
                .andExpect(jsonPath("$.message").value("left"));
    }

    @Test
    void addGroupMembers_emptyList_returns400Validation() throws Exception {
        mockMvc.perform(post("/v1/conversation/{id}/members", C)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.memberIds").value("Member ids are required"));
    }

    @Test
    void addGroupMembers_and_removeGroupMember_bindArguments() throws Exception {
        when(conversationService.addGroupMembers(C, new GroupMembersRequest(List.of(M))))
                .thenReturn(new BaseResponse<>(null, "added", 200));
        when(conversationService.removeGroupMember(C, M)).thenReturn(new BaseResponse<>(null, "removed", 200));

        mockMvc.perform(post("/v1/conversation/{id}/members", C)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\":[\"" + M + "\"]}"))
                .andExpect(jsonPath("$.message").value("added"));
        mockMvc.perform(delete("/v1/conversation/{id}/members/{memberId}", C, M))
                .andExpect(jsonPath("$.message").value("removed"));
    }

    @Test
    void preAuthorizeGuards_arePresentOnManagementEndpoints() throws Exception {
        assertThat(preAuthorizeOf("createConversation", ConversationRequest.class))
                .isEqualTo("@messageDirectPermission.canCreateConversation(#request)");
        assertThat(preAuthorizeOf("updateGroup", UUID.class, UpdateGroupRequest.class))
                .isEqualTo("@groupPermission.canManageGroup(#conversationId)");
        assertThat(preAuthorizeOf("deleteGroup", UUID.class))
                .isEqualTo("@groupPermission.canManageGroup(#conversationId)");
    }
}
