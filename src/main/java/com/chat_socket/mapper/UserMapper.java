package com.chat_socket.mapper;

import com.chat_socket.config.GlobalMapperConfig;
import com.chat_socket.dto.SignUpRequest;
import com.chat_socket.dto.UserDto;
import com.chat_socket.entity.User;
import org.mapstruct.Mapper;

@Mapper(config = GlobalMapperConfig.class)
public interface UserMapper {
    User toEntity(SignUpRequest signUpRequest);

    UserDto toDto(User user);
}
