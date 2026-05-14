package com.chat_socket.service;

import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.UserProfileDto;

public interface UserService {
    BaseResponse<UserProfileDto> getUserProfile();
}
