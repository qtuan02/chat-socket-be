package com.chat_socket.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chat_socket.config.GlobalExceptionHandler;
import com.chat_socket.dto.AuthResponse;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.SignInRequest;
import com.chat_socket.dto.SignUpRequest;
import com.chat_socket.exception.SignInException;
import com.chat_socket.exception.UnAuthorizedException;
import com.chat_socket.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {
    @Mock
    AuthService authService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void signUp_validBody_returnsServiceStatus() throws Exception {
        when(authService.signUp(new SignUpRequest("alice", "a@example.com", "pw", "A", "L")))
                .thenReturn(new BaseResponse<>(null, null, 204));

        mockMvc.perform(post("/v1/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","email":"a@example.com","password":"pw","firstName":"A","lastName":"L"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void signUp_invalidBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/v1/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.username").value("Username is required"))
                .andExpect(jsonPath("$.data.email").value("Email is invalid"))
                .andExpect(jsonPath("$.data.password").value("Password is required"));
    }

    @Test
    void signIn_success_returnsAccessToken() throws Exception {
        when(authService.signIn(eq(new SignInRequest("alice", "pw")), any(HttpServletResponse.class)))
                .thenReturn(new BaseResponse<>(new AuthResponse("token"), "Login successful.", 200));

        mockMvc.perform(post("/v1/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("token"))
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    void signIn_signInException_returns400() throws Exception {
        when(authService.signIn(any(), any(HttpServletResponse.class)))
                .thenThrow(new SignInException("Username or password incorrect!"));

        mockMvc.perform(post("/v1/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"pw\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Username or password incorrect!"));
    }

    @Test
    void signOut_unauthorizedException_returns401() throws Exception {
        when(authService.signOut(any(HttpServletRequest.class), any(HttpServletResponse.class)))
                .thenThrow(new UnAuthorizedException("Token not found."));

        mockMvc.perform(post("/v1/auth/sign-out"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token not found."));
    }

    @Test
    void refresh_returnsServiceBody() throws Exception {
        when(authService.refresh(any(HttpServletRequest.class), any(HttpServletResponse.class)))
                .thenReturn(new BaseResponse<>(new AuthResponse("new"), "Token refreshed successfully.", 200));

        mockMvc.perform(post("/v1/auth/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new"));
    }
}
