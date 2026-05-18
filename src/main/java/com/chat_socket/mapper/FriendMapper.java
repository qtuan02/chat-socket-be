package com.chat_socket.mapper;

import com.chat_socket.config.GlobalMapperConfig;
import com.chat_socket.dto.AcceptFriendResponse;
import com.chat_socket.dto.FriendDto;
import com.chat_socket.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class)
public interface FriendMapper {
    @Mapping(target = "joinedAt", source = "createdAt")
    FriendDto toFriendDto(UserEntity user);

    AcceptFriendResponse toAcceptFriendResponse(UserEntity user);
}
