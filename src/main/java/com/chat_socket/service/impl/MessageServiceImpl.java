package com.chat_socket.service.impl;

import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.DirectMessageRequest;
import com.chat_socket.dto.GroupMessageRequest;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.UpdateMessageRequest;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.entity.MessageEntity;
import com.chat_socket.entity.ParticipantEntity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.enums.MessageType;
import com.chat_socket.exception.BadRequestException;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.mapper.MessageMapper;
import com.chat_socket.repository.ConversationRepository;
import com.chat_socket.repository.MessageRepository;
import com.chat_socket.repository.ParticipantRepository;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.ConversationService;
import com.chat_socket.service.MessageService;
import com.chat_socket.socket.SocketPublisher;
import com.chat_socket.utils.Security;
import java.time.Instant;
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
    private final ConversationService conversationService;

    public MessageServiceImpl(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            ParticipantRepository participantRepository,
            UserRepository userRepository,
            MessageMapper messageMapper,
            SocketPublisher socketPublisher,
            ConversationService conversationService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.messageMapper = messageMapper;
        this.socketPublisher = socketPublisher;
        this.conversationService = conversationService;
    }

    @Override
    @Transactional
    public BaseResponse<MessageDto> sendDirectMessage(DirectMessageRequest request) {
        UUID senderId = Security.getCurrentUser().id();
        validateContent(request.content(), request.type(), request.attachmentUrl());
        if (senderId.equals(request.recipientId()))
            throw new BadRequestException("You cannot send a direct message to yourself.");

        ConversationEntity conversation =
                conversationService.findOrCreateDirectConversation(senderId, request.recipientId());
        UserEntity sender =
                userRepository.findById(senderId).orElseThrow(() -> new NotFoundException("User not found."));
        MessageEntity message =
                createMessage(conversation, sender, request.content(), request.type(), request.attachmentUrl());
        MessageDto messageDto = messageMapper.toDto(message);

        publishMessageCreated(conversation, messageDto);

        return new BaseResponse<>(messageDto, "Message sent successfully.", HttpStatus.CREATED.value());
    }

    @Override
    @Transactional
    public BaseResponse<MessageDto> sendGroupMessage(GroupMessageRequest request) {
        UUID senderId = Security.getCurrentUser().id();
        validateContent(request.content(), request.type(), request.attachmentUrl());

        ConversationEntity conversation = getGroupConversationForSender(request.conversationId(), senderId);
        UserEntity sender =
                userRepository.findById(senderId).orElseThrow(() -> new NotFoundException("User not found."));
        MessageEntity message =
                createMessage(conversation, sender, request.content(), request.type(), request.attachmentUrl());
        MessageDto messageDto = messageMapper.toDto(message);

        publishMessageCreated(conversation, messageDto);

        return new BaseResponse<>(messageDto, "Message sent successfully.", HttpStatus.CREATED.value());
    }

    @Override
    @Transactional
    public BaseResponse<MessageDto> updateMessage(UUID messageId, UpdateMessageRequest request) {
        UUID currentUserId = Security.getCurrentUser().id();
        MessageEntity message = getOwnMessageOrThrow(messageId, currentUserId, "You can only edit your own messages.");
        if (message.getType() != MessageType.TEXT) throw new BadRequestException("Only text messages can be edited.");

        message.setContent(request.content().trim());
        message = messageRepository.save(message);
        MessageDto messageDto = messageMapper.toDto(message);

        UUID conversationId = message.getConversation().getId();
        socketPublisher.publishMessageUpdatedAfterCommit(conversationId, messageDto);
        if (isLastMessage(message)) publishConversationUpdated(conversationId);

        return new BaseResponse<>(messageDto, "Message updated successfully.", HttpStatus.OK.value());
    }

    @Override
    @Transactional
    public BaseResponse<Void> deleteMessage(UUID messageId) {
        UUID currentUserId = Security.getCurrentUser().id();
        MessageEntity message =
                getOwnMessageOrThrow(messageId, currentUserId, "You can only delete your own messages.");
        boolean wasLast = isLastMessage(message);

        message.setDeleted(true);
        message = messageRepository.save(message);
        MessageDto messageDto = messageMapper.toDto(message);

        ConversationEntity conversation = message.getConversation();
        socketPublisher.publishMessageDeletedAfterCommit(conversation.getId(), messageDto);

        if (wasLast) {
            MessageEntity previous = messageRepository
                    .findTopByConversationIdAndDeletedFalseOrderByCreatedAtDescIdDesc(conversation.getId())
                    .orElse(null);
            conversation.setLastMessage(previous);
            if (previous != null) conversation.setLastMessageAt(previous.getCreatedAt());
            conversationRepository.save(conversation);
            publishConversationUpdated(conversation.getId());
        }

        return new BaseResponse<>(null, null, HttpStatus.NO_CONTENT.value());
    }

    private void publishMessageCreated(ConversationEntity conversation, MessageDto messageDto) {
        ConversationEntity updatedConversation = conversationRepository
                .findWithDetails(conversation.getId())
                .orElseThrow(() -> new NotFoundException("Conversation not found."));
        socketPublisher.publishMessageCreatedAfterCommit(updatedConversation, messageDto);
    }

    private MessageEntity getOwnMessageOrThrow(UUID messageId, UUID userId, String forbiddenMessage) {
        MessageEntity message = messageRepository
                .findByIdAndDeletedFalse(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found."));
        if (!message.getSender().getId().equals(userId)) throw new ForbiddenException(forbiddenMessage);
        return message;
    }

    private static boolean isLastMessage(MessageEntity message) {
        MessageEntity last = message.getConversation().getLastMessage();
        return last != null && last.getId().equals(message.getId());
    }

    private void publishConversationUpdated(UUID conversationId) {
        ConversationEntity conversation = conversationRepository
                .findWithDetails(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found."));
        socketPublisher.publishConversationUpdatedAfterCommit(conversation);
    }

    private static void validateContent(String content, MessageType type, String attachmentUrl) {
        boolean hasContent = content != null && !content.isBlank();
        boolean hasAttachment = attachmentUrl != null && !attachmentUrl.isBlank();
        if (!hasContent && !hasAttachment) throw new BadRequestException("Content or attachment is required.");
        if (hasAttachment && type != MessageType.IMAGE && type != MessageType.FILE)
            throw new BadRequestException("Type must be IMAGE or FILE when attaching a file.");
        if (!hasAttachment && type != null && type != MessageType.TEXT)
            throw new BadRequestException("Type must be TEXT without an attachment.");
    }

    private MessageEntity createMessage(
            ConversationEntity conversation,
            UserEntity sender,
            String content,
            MessageType type,
            String attachmentUrl) {
        participantRepository.restoreDeletedParticipantsByConversationId(conversation.getId());

        MessageEntity message = new MessageEntity();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(content);
        message.setAttachmentUrl(attachmentUrl);
        message.setType(type == null ? MessageType.TEXT : type);

        message = messageRepository.saveAndFlush(message);

        Instant messageCreatedAt = message.getCreatedAt() == null ? Instant.now() : message.getCreatedAt();
        conversation.setLastMessage(message);
        conversation.setLastMessageAt(messageCreatedAt);
        conversationRepository.save(conversation);

        markSenderAsRead(conversation, sender, message, messageCreatedAt);

        return message;
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

    private void markSenderAsRead(
            ConversationEntity conversation, UserEntity sender, MessageEntity message, Instant readAt) {
        ParticipantEntity participant = participantRepository
                .findByIdConversationIdAndIdUserId(conversation.getId(), sender.getId())
                .orElseThrow(() -> new NotFoundException("Participant not found."));
        participant.setLastReadMessage(message);
        participant.setLastReadAt(readAt);
        participantRepository.save(participant);
    }
}
