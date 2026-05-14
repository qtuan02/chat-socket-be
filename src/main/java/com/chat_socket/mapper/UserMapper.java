package com.chat_socket.mapper;

import com.chat_socket.config.GlobalMapperConfig;
import com.chat_socket.dto.SignUpRequest;
import com.chat_socket.dto.UserDto;
import com.chat_socket.dto.UserProfileDto;
import com.chat_socket.entity.UserEntity;
import org.mapstruct.Mapper;

@Mapper(config = GlobalMapperConfig.class)
public interface UserMapper {
    UserEntity toEntity(SignUpRequest signUpRequest);

    UserDto toDto(UserEntity user);

    UserProfileDto toUserProfileDto(UserEntity user);
}
