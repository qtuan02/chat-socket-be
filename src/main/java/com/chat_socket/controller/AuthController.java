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
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<BaseResponse<String>> signUp(@Valid @RequestBody SignUpRequest requestEntity) {
        BaseResponse<String> body = authService.signUp(requestEntity);
        return ResponseEntity.status(body.status()).body(body);
    }

    @PostMapping("/sign-in")
    public ResponseEntity<BaseResponse<AuthResponse>> signIn(
            @Valid @RequestBody SignInRequest requestEntity, HttpServletResponse response) {
        BaseResponse<AuthResponse> body = authService.signIn(requestEntity, response);
        return ResponseEntity.status(body.status()).body(body);
    }

    @PostMapping("/sign-out")
    public ResponseEntity<BaseResponse<String>> signOut(HttpServletRequest request, HttpServletResponse response) {
        BaseResponse<String> body = authService.signOut(request, response);
        return ResponseEntity.status(body.status()).body(body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<AuthResponse>> refresh(
            HttpServletRequest request, HttpServletResponse response) {
        BaseResponse<AuthResponse> body = authService.refresh(request, response);
        return ResponseEntity.status(body.status()).body(body);
    }
}
