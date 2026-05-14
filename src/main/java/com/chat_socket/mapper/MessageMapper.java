package com.chat_socket.mapper;

import com.chat_socket.config.GlobalMapperConfig;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.entity.MessageEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class)
public interface MessageMapper {
    @Mapping(target = "conversationId", source = "conversation.id")
    @Mapping(target = "senderId", source = "sender.id")
    MessageDto toDto(MessageEntity message);
}
