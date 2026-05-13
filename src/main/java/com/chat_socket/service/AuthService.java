package com.chat_socket.service;

import com.chat_socket.dto.AuthResponse;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.SignInRequest;
import com.chat_socket.dto.SignUpRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {
    BaseResponse<String> signUp(SignUpRequest request);

    BaseResponse<AuthResponse> signIn(SignInRequest request, HttpServletResponse response);

    BaseResponse<String> signOut(HttpServletRequest request, HttpServletResponse response);
}
