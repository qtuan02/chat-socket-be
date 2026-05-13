package com.chat_socket.service.impl;

import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.UserDto;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.mapper.UserMapper;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.UserService;
import com.chat_socket.utils.SecurityContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Override
    public BaseResponse<UserDto> getUserProfile() {
        UserSecurity currentUser = SecurityContext.getCurrentUser();
        UserDto userProfile = userRepository
                .findById(currentUser.id())
                .map(userMapper::toDto)
                .orElseThrow(() -> new NotFoundException("User not found"));

        return new BaseResponse<>(userProfile, null, HttpStatus.OK.value());
    }
}
