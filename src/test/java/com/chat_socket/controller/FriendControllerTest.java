package com.chat_socket.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chat_socket.config.GlobalExceptionHandler;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.FriendActionRequest;
import com.chat_socket.dto.FriendRequestResponse;
import com.chat_socket.dto.FriendSendRequest;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.service.FriendService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class FriendControllerTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    FriendService friendService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FriendController(friendService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getListFriend_bindsPaginationAndSearch() throws Exception {
        when(friendService.getListFriend(new PaginationRequest(20, null, 0), "an"))
                .thenReturn(new BaseResponse<>(PaginationResponse.offset(List.of(), 20), "Success.", 200));

        mockMvc.perform(get("/v1/friend")
                        .param("limit", "20")
                        .param("offset", "0")
                        .param("search", "an"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextOffset").value(20));
    }

    @Test
    void getListFriendRequest_returnsBothLists() throws Exception {
        when(friendService.getListFriendRequest())
                .thenReturn(new BaseResponse<>(new FriendRequestResponse(List.of(), List.of()), "Success.", 200));

        mockMvc.perform(get("/v1/friend/request"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sentRequests").isEmpty())
                .andExpect(jsonPath("$.data.receivedRequests").isEmpty());
    }

    @Test
    void sendFriendRequest_validBody_returnsServiceStatus() throws Exception {
        when(friendService.sendFriendRequest(new FriendSendRequest(ID, "hi")))
                .thenReturn(new BaseResponse<>(null, "Friend request sent successfully.", 201));

        mockMvc.perform(post("/v1/friend/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":\"" + ID + "\",\"message\":\"hi\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void sendFriendRequest_missingToUserId_returns400Validation() throws Exception {
        mockMvc.perform(post("/v1/friend/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.toUserId").value("To User is required"));
    }

    @Test
    void acceptFriendRequest_notFound_returns404() throws Exception {
        when(friendService.acceptFriendRequest(new FriendActionRequest(ID)))
                .thenThrow(new NotFoundException("Friend request not found."));

        mockMvc.perform(post("/v1/friend/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + ID + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Friend request not found."));
    }

    @Test
    void declineAndCancel_returnServiceStatus() throws Exception {
        when(friendService.declineFriendRequest(new FriendActionRequest(ID)))
                .thenReturn(new BaseResponse<>(null, null, 204));
        when(friendService.cancelFriendRequest(new FriendActionRequest(ID)))
                .thenReturn(new BaseResponse<>(null, "You are not authorized to cancel this request.", 403));

        mockMvc.perform(post("/v1/friend/decline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + ID + "\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/v1/friend/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + ID + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteFriend_passesPathVariable() throws Exception {
        when(friendService.deleteFriend(ID)).thenReturn(new BaseResponse<>(null, null, 204));

        mockMvc.perform(delete("/v1/friend/{friendId}", ID)).andExpect(status().isNoContent());
    }
}
