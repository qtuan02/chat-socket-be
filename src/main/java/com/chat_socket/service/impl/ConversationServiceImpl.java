package com.chat_socket.service.impl;

import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.ConversationDto;
import com.chat_socket.dto.ConversationParticipantDto;
import com.chat_socket.dto.ConversationRequest;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.dto.UserPair;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.entity.MessageEntity;
import com.chat_socket.entity.ParticipantEntity;
import com.chat_socket.entity.ParticipantIdEntity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.enums.ParticipantRole;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.mapper.MessageMapper;
import com.chat_socket.repository.ConversationRepository;
import com.chat_socket.repository.MessageRepository;
import com.chat_socket.repository.ParticipantRepository;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.ConversationService;
import com.chat_socket.utils.Normalize;
import com.chat_socket.utils.PaginationUtils;
import com.chat_socket.utils.Security;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationServiceImpl implements ConversationService {
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final MessageMapper messageMapper;

    public ConversationServiceImpl(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            ParticipantRepository participantRepository,
            UserRepository userRepository,
            MessageMapper messageMapper) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.messageMapper = messageMapper;
    }

    @Override
    public BaseResponse<PaginationResponse<ConversationDto>> getConversations(
            PaginationRequest request, ConversationType type) {
        UserSecurity currentUser = Security.getCurrentUser();

        PaginationUtils.CursorPage page = PaginationUtils.resolveCursorPage(request);

        List<UUID> fetchedConversationIds = page.cursor() == null
                ? conversationRepository.findActiveConversationIdsForUser(currentUser.id(), type, page.pageRequest())
                : conversationRepository.findActiveConversationIdsForUserBeforeCursor(
                        currentUser.id(), type, page.cursor(), page.pageRequest());

        if (fetchedConversationIds.isEmpty())
            return new BaseResponse<>(
                    new PaginationResponse<>(List.of(), null),
                    "Conversations retrieved successfully.",
                    HttpStatus.OK.value());

        List<UUID> conversationIds = page.items(fetchedConversationIds);
        Map<UUID, ConversationEntity> conversationsById =
                conversationRepository.findConversationsWithDetails(conversationIds).stream()
                        .collect(Collectors.toMap(ConversationEntity::getId, Function.identity()));
        Map<UUID, Long> unreadCounts =
                messageRepository.countUnreadMessagesByConversation(currentUser.id(), conversationIds).stream()
                        .collect(Collectors.toMap(
                                MessageRepository.UnreadCountProjection::getConversationId,
                                MessageRepository.UnreadCountProjection::getUnreadCount));

        PaginationResponse<ConversationDto> body = PaginationUtils.toCursorResponse(
                fetchedConversationIds,
                page,
                conversationId -> {
                    ConversationEntity conversation = conversationsById.get(conversationId);
                    return toResponseDto(conversation, unreadCounts.getOrDefault(conversationId, 0L));
                },
                conversationId -> conversationCursor(conversationsById.get(conversationId)),
                false);

        return new BaseResponse<>(body, "Conversations retrieved successfully.", HttpStatus.OK.value());
    }

    @Override
    @Transactional
    public BaseResponse<ConversationDto> createConversation(ConversationRequest request) {
        UserSecurity currentUser = Security.getCurrentUser();

        if (request.type() == ConversationType.DIRECT)
            return createDirectConversation(currentUser.id(), request.memberIds());

        if (request.type() == ConversationType.GROUP)
            return createGroupConversation(currentUser.id(), request.name(), request.memberIds());

        return new BaseResponse<>(null, "Conversation type is invalid.", HttpStatus.BAD_REQUEST.value());
    }

    @Override
    public BaseResponse<PaginationResponse<MessageDto>> getMessages(UUID conversationId, PaginationRequest request) {
        UserSecurity currentUser = Security.getCurrentUser();

        PaginationUtils.CursorPage page = PaginationUtils.resolveCursorPage(request);

        ensureCanReadConversation(conversationId, currentUser.id());

        List<MessageEntity> fetchedMessages = page.cursor() == null
                ? messageRepository.findLatestMessages(conversationId, page.pageRequest())
                : messageRepository.findMessagesBeforeCursor(conversationId, page.cursor(), page.pageRequest());

        PaginationResponse<MessageDto> body = PaginationUtils.toCursorResponse(
                fetchedMessages, page, messageMapper::toDto, MessageEntity::getCreatedAt, true);
        return new BaseResponse<>(body, "Messages retrieved successfully.", HttpStatus.OK.value());
    }

    private LocalDateTime conversationCursor(ConversationEntity conversation) {
        return conversation.getLastMessageAt() == null ? conversation.getUpdatedAt() : conversation.getLastMessageAt();
    }

    private void ensureCanReadConversation(UUID conversationId, UUID userId) {
        if (!conversationRepository.existsById(conversationId)) throw new NotFoundException("Conversation not found.");

        if (!participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                conversationId, userId))
            throw new ForbiddenException("You are not a participant of this conversation.");
    }

    private BaseResponse<ConversationDto> createDirectConversation(UUID currentUserId, List<UUID> memberIds) {
        if (memberIds.size() != 1)
            return new BaseResponse<>(
                    null, "Direct conversation requires exactly one member.", HttpStatus.BAD_REQUEST.value());

        UUID participantId = memberIds.getFirst();
        if (currentUserId.equals(participantId))
            return new BaseResponse<>(
                    null, "You cannot create a direct conversation with yourself.", HttpStatus.BAD_REQUEST.value());

        UserEntity currentUser =
                userRepository.findById(currentUserId).orElseThrow(() -> new NotFoundException("User not found."));
        UserEntity participant =
                userRepository.findById(participantId).orElseThrow(() -> new NotFoundException("Member not found."));

        UserPair pair = Normalize.normalizeUserPair(currentUserId, participantId);
        ConversationEntity conversation = conversationRepository
                .findDirectConversation(ConversationType.DIRECT, pair.userAId(), pair.userBId())
                .orElseGet(() -> createDirectConversation(currentUser, participant, pair));

        return new BaseResponse<>(
                toResponseDto(conversation), "Conversation created successfully.", HttpStatus.CREATED.value());
    }

    private BaseResponse<ConversationDto> createGroupConversation(
            UUID currentUserId, String name, List<UUID> memberIds) {
        if (name == null || name.isBlank())
            return new BaseResponse<>(null, "Group name is required.", HttpStatus.BAD_REQUEST.value());

        UserEntity currentUser =
                userRepository.findById(currentUserId).orElseThrow(() -> new NotFoundException("User not found."));

        LinkedHashSet<UUID> participantIds = new LinkedHashSet<>();
        participantIds.add(currentUserId);
        participantIds.addAll(memberIds);

        Map<UUID, UserEntity> usersById = userRepository.findAllById(participantIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

        if (usersById.size() != participantIds.size())
            throw new NotFoundException("One or more members were not found.");

        ConversationEntity conversation = new ConversationEntity();
        conversation.setType(ConversationType.GROUP);
        conversation.setGroupName(name.trim());
        conversation.setCreatedBy(currentUser);
        conversation.setLastMessageAt(LocalDateTime.now());

        conversation = conversationRepository.saveAndFlush(conversation);

        List<ParticipantEntity> participants = new ArrayList<>();
        participants.add(createParticipant(conversation, currentUser, ParticipantRole.ADMIN));
        for (UUID participantId : participantIds) {
            if (!participantId.equals(currentUserId))
                participants.add(createParticipant(conversation, usersById.get(participantId), ParticipantRole.MEMBER));
        }
        participantRepository.flush();
        conversation.setParticipants(participants);

        return new BaseResponse<>(
                toResponseDto(conversation), "Conversation created successfully.", HttpStatus.CREATED.value());
    }

    private ConversationEntity createDirectConversation(UserEntity currentUser, UserEntity participant, UserPair pair) {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setType(ConversationType.DIRECT);
        conversation.setCreatedBy(currentUser);
        conversation.setLastMessageAt(LocalDateTime.now());

        if (pair.userAId().equals(currentUser.getId())) {
            conversation.setDirectUserA(currentUser);
            conversation.setDirectUserB(participant);
        } else {
            conversation.setDirectUserA(participant);
            conversation.setDirectUserB(currentUser);
        }

        conversation = conversationRepository.saveAndFlush(conversation);

        List<ParticipantEntity> participants = List.of(
                createParticipant(conversation, currentUser, ParticipantRole.MEMBER),
                createParticipant(conversation, participant, ParticipantRole.MEMBER));
        participantRepository.flush();
        conversation.setParticipants(participants);
        return conversation;
    }

    private ParticipantEntity createParticipant(
            ConversationEntity conversation, UserEntity user, ParticipantRole participantRole) {
        ParticipantEntity participant = new ParticipantEntity();
        participant.setId(new ParticipantIdEntity(conversation.getId(), user.getId()));
        participant.setConversation(conversation);
        participant.setUser(user);
        participant.setRole(participantRole);
        return participantRepository.save(participant);
    }

    private ConversationDto toResponseDto(ConversationEntity conversation) {
        return toResponseDto(conversation, 0);
    }

    private ConversationDto toResponseDto(ConversationEntity conversation, long unreadCount) {
        MessageEntity lastMessage = conversation.getLastMessage();
        return new ConversationDto(
                conversation.getId(),
                conversation.getType(),
                conversation.getGroupName(),
                conversation.getCreatedBy() == null
                        ? null
                        : conversation.getCreatedBy().getId(),
                conversation.getDirectUserA() == null
                        ? null
                        : conversation.getDirectUserA().getId(),
                conversation.getDirectUserB() == null
                        ? null
                        : conversation.getDirectUserB().getId(),
                conversation.getLastMessage() == null
                        ? null
                        : conversation.getLastMessage().getId(),
                lastMessage == null ? null : messageMapper.toDto(lastMessage),
                conversation.getLastMessageAt(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                unreadCount,
                conversation.getParticipants().stream()
                        .map(p -> {
                            UserEntity user = p.getUser();
                            return new ConversationParticipantDto(
                                    user.getId(),
                                    user.getFirstName(),
                                    user.getLastName(),
                                    user.getAvatarUrl(),
                                    p.getRole(),
                                    p.getJoinedAt());
                        })
                        .toList());
    }
}
