package com.chat_socket.mapper;

import com.chat_socket.config.GlobalMapperConfig;
import com.chat_socket.dto.FriendRequestReceviedDto;
import com.chat_socket.dto.FriendRequestSentDto;
import com.chat_socket.dto.FriendSendRequest;
import com.chat_socket.entity.FriendRequestEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class, uses = FriendMapper.class)
public interface FriendRequestMapper {
    FriendRequestEntity toEntity(FriendSendRequest request);

    @Mapping(target = "fromUser", source = "fromUser.id")
    @Mapping(target = "toUser", source = "toUser")
    FriendRequestSentDto toSentDto(FriendRequestEntity request);

    @Mapping(target = "toUser", source = "toUser.id")
    @Mapping(target = "fromUser", source = "fromUser")
    FriendRequestReceviedDto toReceivedDto(FriendRequestEntity request);
}
