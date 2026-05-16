package com.chat_socket.service.impl;

import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.MessageRequest;
import com.chat_socket.dto.UserPair;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.entity.MessageEntity;
import com.chat_socket.entity.ParticipantEntity;
import com.chat_socket.entity.ParticipantIdEntity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.enums.MessageType;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.mapper.MessageMapper;
import com.chat_socket.repository.ConversationRepository;
import com.chat_socket.repository.MessageRepository;
import com.chat_socket.repository.ParticipantRepository;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.MessageService;
import com.chat_socket.socket.SocketPublisher;
import com.chat_socket.utils.Normalize;
import com.chat_socket.utils.Security;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageServiceImpl implements MessageService {
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final MessageMapper messageMapper;
    private final SocketPublisher socketPublisher;

    public MessageServiceImpl(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            ParticipantRepository participantRepository,
            UserRepository userRepository,
            MessageMapper messageMapper,
            SocketPublisher socketPublisher) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.messageMapper = messageMapper;
        this.socketPublisher = socketPublisher;
    }

    @Override
    @Transactional
    public BaseResponse<MessageDto> sendDirectMessage(MessageRequest request) {
        UserSecurity currentUser = Security.getCurrentUser();
        UUID senderId = currentUser.id();

        if (request.content() == null || request.content().isBlank())
            return new BaseResponse<>(null, "Content is required.", HttpStatus.BAD_REQUEST.value());

        if (request.conversationId() == null && request.recipientId() == null)
            return new BaseResponse<>(null, "Recipient is required.", HttpStatus.BAD_REQUEST.value());

        if (request.conversationId() == null && senderId.equals(request.recipientId()))
            return new BaseResponse<>(
                    null, "You cannot send a direct message to yourself.", HttpStatus.BAD_REQUEST.value());

        ConversationEntity conversation = request.conversationId() != null
                ? getDirectConversationForSender(request.conversationId(), senderId)
                : findOrCreateDirectConversation(senderId, request.recipientId());

        UserEntity sender =
                userRepository.findById(senderId).orElseThrow(() -> new NotFoundException("User not found."));

        MessageEntity message = createMessage(conversation, sender, request);
        MessageDto messageDto = messageMapper.toDto(message);

        socketPublisher.publishMessageAfterCommit(
                conversation.getId(), messageMapper.toDto(message), conversation.getLastMessageAt());

        return new BaseResponse<>(messageDto, "Message sent successfully.", HttpStatus.CREATED.value());
    }

    @Override
    @Transactional
    public BaseResponse<MessageDto> sendGroupMessage(MessageRequest request) {
        UserSecurity currentUser = Security.getCurrentUser();
        UUID senderId = currentUser.id();

        if (request.content() == null || request.content().isBlank())
            return new BaseResponse<>(null, "Content is required.", HttpStatus.BAD_REQUEST.value());

        if (request.conversationId() == null)
            return new BaseResponse<>(null, "Conversation is required.", HttpStatus.BAD_REQUEST.value());

        ConversationEntity conversation = getGroupConversationForSender(request.conversationId(), senderId);
        UserEntity sender =
                userRepository.findById(senderId).orElseThrow(() -> new NotFoundException("User not found."));

        MessageEntity message = createMessage(conversation, sender, request);
        MessageDto messageDto = messageMapper.toDto(message);

        socketPublisher.publishMessageAfterCommit(conversation.getId(), messageDto, conversation.getLastMessageAt());

        return new BaseResponse<>(messageDto, "Message sent successfully.", HttpStatus.CREATED.value());
    }

    private MessageEntity createMessage(ConversationEntity conversation, UserEntity sender, MessageRequest request) {
        MessageEntity message = new MessageEntity();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(request.content());
        message.setAttachmentUrl(request.attachmentUrl());
        message.setType(request.type() == null ? MessageType.TEXT : request.type());

        message = messageRepository.saveAndFlush(message);

        LocalDateTime messageCreatedAt = message.getCreatedAt() == null ? LocalDateTime.now() : message.getCreatedAt();
        conversation.setLastMessage(message);
        conversation.setLastMessageAt(messageCreatedAt);
        conversationRepository.save(conversation);

        markSenderAsRead(conversation, sender, message, messageCreatedAt);

        return message;
    }

    private ConversationEntity getDirectConversationForSender(UUID conversationId, UUID senderId) {
        ConversationEntity conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found."));

        if (conversation.getType() != ConversationType.DIRECT)
            throw new NotFoundException("Direct conversation not found.");

        if (!participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                conversationId, senderId))
            throw new ForbiddenException("You are not a participant of this conversation.");

        return conversation;
    }

    private ConversationEntity getGroupConversationForSender(UUID conversationId, UUID senderId) {
        ConversationEntity conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found."));

        if (conversation.getType() != ConversationType.GROUP)
            throw new NotFoundException("Group conversation not found.");

        if (!participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                conversationId, senderId))
            throw new ForbiddenException("You are not a participant of this conversation.");

        return conversation;
    }

    private ConversationEntity findOrCreateDirectConversation(UUID senderId, UUID recipientId) {
        UserEntity sender =
                userRepository.findById(senderId).orElseThrow(() -> new NotFoundException("User not found."));
        UserEntity recipient =
                userRepository.findById(recipientId).orElseThrow(() -> new NotFoundException("Recipient not found."));

        UserPair pair = Normalize.normalizeUserPair(senderId, recipientId);
        return conversationRepository
                .findDirectConversation(ConversationType.DIRECT, pair.userAId(), pair.userBId())
                .orElseGet(() -> createDirectConversation(sender, recipient));
    }

    private ConversationEntity createDirectConversation(UserEntity sender, UserEntity recipient) {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setType(ConversationType.DIRECT);
        conversation.setCreatedBy(sender);
        conversation.setDirectUserA(sender);
        conversation.setDirectUserB(recipient);

        conversation = conversationRepository.saveAndFlush(conversation);
        createParticipant(conversation, sender);
        createParticipant(conversation, recipient);
        return conversation;
    }

    private void createParticipant(ConversationEntity conversation, UserEntity user) {
        ParticipantEntity participant = new ParticipantEntity();
        participant.setId(new ParticipantIdEntity(conversation.getId(), user.getId()));
        participant.setConversation(conversation);
        participant.setUser(user);
        participantRepository.save(participant);
    }

    private void markSenderAsRead(
            ConversationEntity conversation, UserEntity sender, MessageEntity message, LocalDateTime readAt) {
        ParticipantEntity participant = participantRepository
                .findByIdConversationIdAndIdUserId(conversation.getId(), sender.getId())
                .orElseThrow(() -> new NotFoundException("Participant not found."));
        participant.setLastReadMessage(message);
        participant.setLastReadAt(readAt);
        participantRepository.save(participant);
    }
}
