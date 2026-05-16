package com.chat_socket.controller;

import com.chat_socket.constant.RouteApi;
import com.chat_socket.dto.BaseResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RouteApi.HEALTH_API)
public class HealthController {
    @GetMapping
    public ResponseEntity<BaseResponse<String>> getHealthCheck() {
        BaseResponse<String> body = new BaseResponse<>("OK", "Server is healthy", HttpStatus.OK.value());
        return ResponseEntity.status(body.status()).body(body);
    }
}
