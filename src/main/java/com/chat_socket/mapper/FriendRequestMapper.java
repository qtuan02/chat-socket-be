package com.chat_socket.mapper;

import com.chat_socket.config.GlobalMapperConfig;
import com.chat_socket.dto.FriendSendRequest;
import com.chat_socket.entity.FriendRequestEntity;
import org.mapstruct.Mapper;

@Mapper(config = GlobalMapperConfig.class)
public interface FriendRequestMapper {
    FriendRequestEntity toEntity(FriendSendRequest request);
}
