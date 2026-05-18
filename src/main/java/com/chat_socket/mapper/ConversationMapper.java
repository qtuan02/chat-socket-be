package com.chat_socket.mapper;

import com.chat_socket.config.GlobalMapperConfig;
import com.chat_socket.dto.ConversationDto;
import com.chat_socket.dto.ConversationParticipantDto;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.entity.ParticipantEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class, uses = MessageMapper.class)
public interface ConversationMapper {
    default ConversationDto toDto(ConversationEntity conversation) {
        return toDto(conversation, 0);
    }

    @Mapping(target = "createdById", source = "conversation.createdBy.id")
    @Mapping(target = "directUserAId", source = "conversation.directUserA.id")
    @Mapping(target = "directUserBId", source = "conversation.directUserB.id")
    @Mapping(target = "lastMessageId", source = "conversation.lastMessage.id")
    @Mapping(target = "lastMessage", source = "conversation.lastMessage")
    @Mapping(target = "unreadCount", source = "unreadCount")
    ConversationDto toDto(ConversationEntity conversation, long unreadCount);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "firstName", source = "user.firstName")
    @Mapping(target = "lastName", source = "user.lastName")
    @Mapping(target = "avatarUrl", source = "user.avatarUrl")
    @Mapping(target = "lastReadMessageId", source = "lastReadMessage.id")
    ConversationParticipantDto toParticipantDto(ParticipantEntity participant);
}
