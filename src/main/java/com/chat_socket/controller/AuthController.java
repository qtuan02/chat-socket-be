package com.chat_socket.controller;

import com.chat_socket.constant.RouteApi;
import com.chat_socket.dto.AuthResponse;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.SignInRequest;
import com.chat_socket.dto.SignUpRequest;
import com.chat_socket.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RouteApi.AUTH_API)
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/sign-up")
    public BaseResponse<String> signUp(@Valid @RequestBody SignUpRequest requestEntity) {
        return authService.signUp(requestEntity);
    }

    @PostMapping("/sign-in")
    public BaseResponse<AuthResponse> signIn(
            @Valid @RequestBody SignInRequest requestEntity, HttpServletResponse response) {
        return authService.signIn(requestEntity, response);
    }

    @PostMapping("/sign-out")
    public BaseResponse<String> signOut(HttpServletRequest request, HttpServletResponse response) {
        return authService.signOut(request, response);
    }
}
