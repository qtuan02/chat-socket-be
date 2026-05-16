package com.chat_socket.service;

import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.UpdateUserRequest;
import com.chat_socket.dto.UserInfoDto;
import com.chat_socket.dto.UserProfileDto;
import java.util.UUID;

public interface UserService {
    BaseResponse<UserProfileDto> getUserProfile();

    BaseResponse<UserProfileDto> updateUserProfile(UpdateUserRequest request);

    BaseResponse<UserInfoDto> getUserInfo(UUID userId);
}
