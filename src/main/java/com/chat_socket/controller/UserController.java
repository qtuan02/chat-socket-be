package com.chat_socket.controller;

import com.chat_socket.constant.RouteApi;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.dto.UpdateUserRequest;
import com.chat_socket.dto.UserInfoDto;
import com.chat_socket.dto.UserProfileDto;
import com.chat_socket.dto.UserSearchDto;
import com.chat_socket.service.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RouteApi.USER_API)
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<BaseResponse<UserProfileDto>> getMe() {
        BaseResponse<UserProfileDto> body = userService.getUserProfile();
        return ResponseEntity.status(body.status()).body(body);
    }

    @PatchMapping("/me")
    public ResponseEntity<BaseResponse<UserProfileDto>> updateMe(@Valid @RequestBody UpdateUserRequest request) {
        BaseResponse<UserProfileDto> body = userService.updateUserProfile(request);
        return ResponseEntity.status(body.status()).body(body);
    }

    @GetMapping
    public ResponseEntity<BaseResponse<PaginationResponse<UserSearchDto>>> searchUsers(
            @ModelAttribute PaginationRequest request, @RequestParam(required = false) String search) {
        BaseResponse<PaginationResponse<UserSearchDto>> body = userService.searchUsers(request, search);
        return ResponseEntity.status(body.status()).body(body);
    }

    @GetMapping("/info")
    public ResponseEntity<BaseResponse<UserInfoDto>> getInfo(@RequestParam UUID userId) {
        BaseResponse<UserInfoDto> body = userService.getUserInfo(userId);
        return ResponseEntity.status(body.status()).body(body);
    }
}
