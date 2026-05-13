package com.chat_socket.service;

import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.UserDto;

public interface UserService {
    BaseResponse<UserDto> getUserProfile();
}
