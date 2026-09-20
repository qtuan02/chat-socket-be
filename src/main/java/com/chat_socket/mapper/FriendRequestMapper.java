package com.chat_socket.mapper;

import com.chat_socket.config.GlobalMapperConfig;
import com.chat_socket.dto.FriendRequestDto;
import com.chat_socket.dto.FriendSendRequest;
import com.chat_socket.entity.FriendRequestEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class, uses = UserMapper.class)
public interface FriendRequestMapper {
    FriendRequestEntity toEntity(FriendSendRequest request);

    @Mapping(target = "user", source = "toUser")
    FriendRequestDto toSentDto(FriendRequestEntity request);

    @Mapping(target = "user", source = "fromUser")
    FriendRequestDto toReceivedDto(FriendRequestEntity request);
}
