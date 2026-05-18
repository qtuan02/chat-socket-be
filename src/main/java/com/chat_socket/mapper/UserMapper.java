package com.chat_socket.mapper;

import com.chat_socket.config.GlobalMapperConfig;
import com.chat_socket.dto.SignUpRequest;
import com.chat_socket.dto.UserDto;
import com.chat_socket.dto.UserInfoDto;
import com.chat_socket.dto.UserProfileDto;
import com.chat_socket.dto.UserSearchDto;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.enums.FriendStatus;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class)
public interface UserMapper {
    UserEntity toEntity(SignUpRequest signUpRequest);

    UserDto toDto(UserEntity user);

    UserProfileDto toUserProfileDto(UserEntity user);

    @Mapping(target = "joinedAt", source = "user.createdAt")
    @Mapping(target = "statusFriend", source = "statusFriend")
    UserInfoDto toUserInfoDto(UserEntity user, FriendStatus statusFriend);

    @Mapping(target = "joinedAt", source = "user.createdAt")
    @Mapping(target = "statusFriend", source = "statusFriend")
    @Mapping(target = "requestId", source = "requestId")
    UserSearchDto toUserSearchDto(UserEntity user, FriendStatus statusFriend, UUID requestId);
}
