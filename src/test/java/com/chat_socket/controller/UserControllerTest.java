package com.chat_socket.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chat_socket.config.GlobalExceptionHandler;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.dto.UpdateUserRequest;
import com.chat_socket.dto.UserProfileDto;
import com.chat_socket.exception.BadRequestException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.service.UserService;
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
class UserControllerTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UserProfileDto PROFILE =
            new UserProfileDto(ID, "alice", "A", "L", "a@example.com", null, null, null);

    @Mock
    UserService userService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getMe_returnsProfile() throws Exception {
        when(userService.getUserProfile()).thenReturn(new BaseResponse<>(PROFILE, null, 200));

        mockMvc.perform(get("/v1/user/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice"));
    }

    @Test
    void getMe_notFound_returns404() throws Exception {
        when(userService.getUserProfile()).thenThrow(new NotFoundException("User not found"));

        mockMvc.perform(get("/v1/user/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void updateMe_bindsBodyAndReturnsServiceStatus() throws Exception {
        UpdateUserRequest expected = new UpdateUserRequest(null, null, "Anna", null, null, null, null, null);
        when(userService.updateUserProfile(expected)).thenReturn(new BaseResponse<>(PROFILE, "ok", 200));

        mockMvc.perform(patch("/v1/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Anna\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateMe_badRequestException_returns400() throws Exception {
        when(userService.updateUserProfile(new UpdateUserRequest("taken", null, null, null, null, null, null, null)))
                .thenThrow(new BadRequestException("Username already exists"));

        mockMvc.perform(patch("/v1/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"taken\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username already exists"));
    }

    @Test
    void updateMe_tooLongUsername_returns400Validation() throws Exception {
        mockMvc.perform(patch("/v1/user/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + "x".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.username").value("Username must be at most 50 characters"));
    }

    @Test
    void searchUsers_bindsQueryParamsIntoPaginationRequest() throws Exception {
        when(userService.searchUsers(new PaginationRequest(5, null, 10), "bob"))
                .thenReturn(new BaseResponse<>(PaginationResponse.offset(List.of(), null), "Success.", 200));

        mockMvc.perform(get("/v1/user")
                        .param("limit", "5")
                        .param("offset", "10")
                        .param("search", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages").isEmpty());
    }

    @Test
    void getInfo_passesUserIdParam() throws Exception {
        when(userService.getUserInfo(ID)).thenReturn(new BaseResponse<>(null, "Success.", 200));

        mockMvc.perform(get("/v1/user/info").param("userId", ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Success."));
    }
}
