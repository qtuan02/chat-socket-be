package com.chat_socket.service;

import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.dto.UpdateUserRequest;
import com.chat_socket.dto.UserInfoDto;
import com.chat_socket.dto.UserProfileDto;
import com.chat_socket.dto.UserSearchDto;
import java.util.UUID;

public interface UserService {
    BaseResponse<UserProfileDto> getUserProfile();

    BaseResponse<UserProfileDto> updateUserProfile(UpdateUserRequest request);

    BaseResponse<UserInfoDto> getUserInfo(UUID userId);

    BaseResponse<PaginationResponse<UserSearchDto>> searchUsers(PaginationRequest request, String search);
}
