package com.chat_socket.service.impl;

import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.ConversationDto;
import com.chat_socket.dto.ConversationRequest;
import com.chat_socket.dto.GroupMembersRequest;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.dto.UpdateGroupRequest;
import com.chat_socket.dto.UserPair;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.entity.MessageEntity;
import com.chat_socket.entity.ParticipantEntity;
import com.chat_socket.entity.ParticipantIdEntity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.enums.ParticipantRole;
import com.chat_socket.exception.BadRequestException;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.FriendPermissionException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.mapper.ConversationMapper;
import com.chat_socket.mapper.MessageMapper;
import com.chat_socket.repository.ConversationRepository;
import com.chat_socket.repository.FriendRepository;
import com.chat_socket.repository.MessageRepository;
import com.chat_socket.repository.ParticipantRepository;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.ConversationService;
import com.chat_socket.socket.SocketPublisher;
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
    private final FriendRepository friendRepository;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final SocketPublisher socketPublisher;

    public ConversationServiceImpl(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            ParticipantRepository participantRepository,
            UserRepository userRepository,
            FriendRepository friendRepository,
            ConversationMapper conversationMapper,
            MessageMapper messageMapper,
            SocketPublisher socketPublisher) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.friendRepository = friendRepository;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.socketPublisher = socketPublisher;
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
                conversationId -> {
                    ConversationEntity conversation = conversationsById.get(conversationId);
                    return conversation.getLastMessageAt() == null
                            ? conversation.getUpdatedAt()
                            : conversation.getLastMessageAt();
                },
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

    @Override
    @Transactional
    public BaseResponse<Void> markAsSeen(UUID conversationId) {
        UserSecurity currentUser = Security.getCurrentUser();
        ConversationEntity conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found."));
        ParticipantEntity participant = participantRepository
                .findByIdConversationIdAndIdUserId(conversationId, currentUser.id())
                .orElseThrow(() -> new ForbiddenException("You are not a participant of this conversation."));

        if (participant.getLeftAt() != null || participant.getDeletedAt() != null)
            throw new ForbiddenException("You are not a participant of this conversation.");

        MessageEntity lastMessage = conversation.getLastMessage();
        if (lastMessage == null) return new BaseResponse<>(null, "No messages to mark as seen.", HttpStatus.OK.value());

        if (participant.getLastReadMessage() != null
                && participant.getLastReadMessage().getId().equals(lastMessage.getId()))
            return new BaseResponse<>(null, "Messages already marked as seen.", HttpStatus.OK.value());

        LocalDateTime seenAt = LocalDateTime.now();

        participant.setLastReadMessage(lastMessage);
        participant.setLastReadAt(seenAt);
        participantRepository.save(participant);

        socketPublisher.publishConversationSeenAfterCommit(
                conversationId,
                currentUser.id(),
                messageMapper.toDto(lastMessage),
                conversation.getLastMessageAt(),
                seenAt);

        return new BaseResponse<>(null, "Messages marked as seen successfully.", HttpStatus.OK.value());
    }

    @Override
    @Transactional
    public BaseResponse<Void> deleteGroup(UUID conversationId) {
        UserSecurity currentUser = Security.getCurrentUser();
        ConversationEntity conversation = getGroupConversationOrThrow(conversationId);
        ParticipantEntity participant = getActiveAdminParticipantOrThrow(conversationId, currentUser.id());

        LocalDateTime deletedAt = LocalDateTime.now();
        MessageEntity lastMessage = conversation.getLastMessage();
        participant.setDeletedAt(deletedAt);
        if (lastMessage != null) {
            participant.setLastReadMessage(lastMessage);
            participant.setLastReadAt(deletedAt);
        }
        participantRepository.save(participant);

        socketPublisher.publishGroupDeletedAfterCommit(conversationId, currentUser.id());

        return new BaseResponse<>(null, "Group deleted successfully.", HttpStatus.OK.value());
    }

    @Override
    @Transactional
    public BaseResponse<ConversationDto> updateGroup(UUID conversationId, UpdateGroupRequest request) {
        if (request == null || request.name() == null || request.name().isBlank())
            throw new BadRequestException("Group name is required.");

        UserSecurity currentUser = Security.getCurrentUser();
        ConversationEntity conversation = getGroupConversationOrThrow(conversationId);
        getActiveAdminParticipantOrThrow(conversationId, currentUser.id());

        conversation.setGroupName(request.name().trim());
        conversationRepository.save(conversation);

        ConversationEntity updatedConversation = findConversationWithDetails(conversationId);

        MessageDto lastMessage =
                conversation.getLastMessage() == null ? null : messageMapper.toDto(conversation.getLastMessage());
        socketPublisher.publishConversationUpdatedAfterCommit(
                conversation.getId(), lastMessage, conversation.getLastMessageAt());

        return new BaseResponse<>(
                toResponseDto(updatedConversation), "Group updated successfully.", HttpStatus.OK.value());
    }

    @Override
    @Transactional
    public BaseResponse<ConversationDto> addGroupMembers(UUID conversationId, GroupMembersRequest request) {
        if (request == null
                || request.memberIds() == null
                || request.memberIds().isEmpty()) throw new BadRequestException("Member ids are required.");

        UserSecurity currentUser = Security.getCurrentUser();
        ConversationEntity conversation = getGroupConversationOrThrow(conversationId);
        getActiveParticipantOrThrow(conversationId, currentUser.id());

        LinkedHashSet<UUID> uniqueMemberIds = new LinkedHashSet<>(request.memberIds());
        uniqueMemberIds.remove(currentUser.id());

        if (uniqueMemberIds.isEmpty()) {
            ConversationEntity updatedConversation = findConversationWithDetails(conversationId);
            return new BaseResponse<>(
                    toResponseDto(updatedConversation), "Members already in group.", HttpStatus.OK.value());
        }

        ensureFriendWithAllMembers(currentUser.id(), new ArrayList<>(uniqueMemberIds));

        Map<UUID, UserEntity> usersById = userRepository.findAllById(uniqueMemberIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        if (usersById.size() != uniqueMemberIds.size())
            throw new NotFoundException("One or more members were not found.");

        Map<UUID, ParticipantEntity> existingParticipants =
                participantRepository.findByConversationIdAndIdUserIdIn(conversationId, uniqueMemberIds).stream()
                        .collect(Collectors.toMap(
                                participant -> participant.getId().getUserId(), Function.identity()));

        boolean hasChanges = false;
        LocalDateTime now = LocalDateTime.now();
        for (UUID memberId : uniqueMemberIds) {
            ParticipantEntity participant = existingParticipants.get(memberId);
            if (participant == null) {
                createParticipant(conversation, usersById.get(memberId), ParticipantRole.MEMBER);
                hasChanges = true;
                continue;
            }

            if (participant.getLeftAt() != null || participant.getDeletedAt() != null) {
                participant.setLeftAt(null);
                participant.setDeletedAt(null);
                participant.setRole(ParticipantRole.MEMBER);
                participant.setJoinedAt(now);
                participantRepository.save(participant);
                hasChanges = true;
            }
        }

        if (hasChanges) conversationRepository.save(conversation);

        ConversationEntity updatedConversation = findConversationWithDetails(conversationId);

        MessageDto lastMessage =
                conversation.getLastMessage() == null ? null : messageMapper.toDto(conversation.getLastMessage());
        socketPublisher.publishConversationUpdatedAfterCommit(
                conversation.getId(), lastMessage, conversation.getLastMessageAt());

        return new BaseResponse<>(
                toResponseDto(updatedConversation), "Members added successfully.", HttpStatus.OK.value());
    }

    @Override
    @Transactional
    public BaseResponse<ConversationDto> removeGroupMember(UUID conversationId, UUID memberId) {
        if (memberId == null) throw new BadRequestException("Member id is required.");

        UserSecurity currentUser = Security.getCurrentUser();
        ConversationEntity conversation = getGroupConversationOrThrow(conversationId);
        ParticipantEntity currentParticipant = getActiveParticipantOrThrow(conversationId, currentUser.id());
        if (currentParticipant.getRole() != ParticipantRole.ADMIN)
            throw new ForbiddenException("Only admins can manage group members.");

        if (currentUser.id().equals(memberId))
            throw new BadRequestException("You cannot remove yourself. Use leave endpoint instead.");

        ParticipantEntity targetParticipant = participantRepository
                .findByIdConversationIdAndIdUserId(conversationId, memberId)
                .orElseThrow(() -> new NotFoundException("Participant not found."));
        if (targetParticipant.getLeftAt() != null || targetParticipant.getDeletedAt() != null)
            throw new NotFoundException("Participant not found.");
        if (targetParticipant.getRole() == ParticipantRole.ADMIN)
            throw new BadRequestException("You cannot remove an admin from this group.");

        targetParticipant.setLeftAt(LocalDateTime.now());
        participantRepository.save(targetParticipant);
        conversationRepository.save(conversation);

        ConversationEntity updatedConversation = findConversationWithDetails(conversationId);

        MessageDto lastMessage =
                conversation.getLastMessage() == null ? null : messageMapper.toDto(conversation.getLastMessage());
        socketPublisher.publishConversationUpdatedAfterCommit(
                conversation.getId(), lastMessage, conversation.getLastMessageAt());

        return new BaseResponse<>(
                toResponseDto(updatedConversation), "Member removed from group successfully.", HttpStatus.OK.value());
    }

    @Override
    @Transactional
    public BaseResponse<Void> leaveGroup(UUID conversationId) {
        UserSecurity currentUser = Security.getCurrentUser();
        ConversationEntity conversation = getGroupConversationOrThrow(conversationId);
        ParticipantEntity currentParticipant = getActiveParticipantOrThrow(conversationId, currentUser.id());

        if (currentParticipant.getRole() == ParticipantRole.ADMIN) {
            long activeAdmins = participantRepository.countActiveByConversationIdAndRoleAndIdUserIdNot(
                    conversationId, ParticipantRole.ADMIN, currentUser.id());
            if (activeAdmins == 0) throw new BadRequestException("You are the only admin.You can't leaving.");
        }

        currentParticipant.setLeftAt(LocalDateTime.now());
        participantRepository.save(currentParticipant);
        conversationRepository.save(conversation);

        MessageDto lastMessage =
                conversation.getLastMessage() == null ? null : messageMapper.toDto(conversation.getLastMessage());
        socketPublisher.publishConversationUpdatedAfterCommit(
                conversation.getId(), lastMessage, conversation.getLastMessageAt());

        return new BaseResponse<>(null, "Left group successfully.", HttpStatus.OK.value());
    }

    private ConversationEntity getGroupConversationOrThrow(UUID conversationId) {
        ConversationEntity conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found."));

        if (conversation.getType() != ConversationType.GROUP)
            throw new NotFoundException("Group conversation not found.");

        return conversation;
    }

    private ParticipantEntity getActiveParticipantOrThrow(UUID conversationId, UUID userId) {
        ParticipantEntity participant = participantRepository
                .findByIdConversationIdAndIdUserId(conversationId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a participant of this conversation."));

        if (participant.getLeftAt() != null || participant.getDeletedAt() != null)
            throw new ForbiddenException("You are not a participant of this conversation.");

        return participant;
    }

    private ParticipantEntity getActiveAdminParticipantOrThrow(UUID conversationId, UUID userId) {
        ParticipantEntity participant = getActiveParticipantOrThrow(conversationId, userId);
        if (participant.getRole() != ParticipantRole.ADMIN)
            throw new ForbiddenException("Only admins can manage this group.");

        return participant;
    }

    private void ensureFriendWithAllMembers(UUID currentUserId, List<UUID> memberIds) {
        List<UUID> notFriends = new ArrayList<>();
        for (UUID memberId : memberIds) {
            if (memberId == null) continue;

            UserPair pair = Normalize.normalizeUserPair(currentUserId, memberId);
            if (!friendRepository.existsByUserAIdAndUserBId(pair.userAId(), pair.userBId())) {
                notFriends.add(memberId);
            }
        }

        if (!notFriends.isEmpty())
            throw new FriendPermissionException("You can only add friends to a group.", notFriends);
    }

    private ConversationEntity findConversationWithDetails(UUID conversationId) {
        return conversationRepository.findConversationsWithDetails(List.of(conversationId)).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Conversation not found."));
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
        participantRepository.restoreDeletedParticipant(conversation.getId(), currentUserId);
        conversation = findConversationWithDetails(conversation.getId());

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
        return conversationMapper.toDto(conversation);
    }

    private ConversationDto toResponseDto(ConversationEntity conversation, long unreadCount) {
        return conversationMapper.toDto(conversation, unreadCount);
    }
}
