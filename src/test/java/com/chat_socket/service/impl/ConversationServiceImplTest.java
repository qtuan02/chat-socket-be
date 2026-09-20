package com.chat_socket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.ConversationDto;
import com.chat_socket.dto.ConversationRequest;
import com.chat_socket.dto.GroupMembersRequest;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.dto.UpdateGroupRequest;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.entity.MessageEntity;
import com.chat_socket.entity.ParticipantEntity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.enums.MessageType;
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
import com.chat_socket.socket.SocketPublisher;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {
    private static final UUID SMALL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BIG = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final ConversationDto DTO =
            new ConversationDto(C, null, null, null, null, null, null, null, null, null, null, 0, List.of());

    @Mock
    ConversationRepository conversationRepository;

    @Mock
    MessageRepository messageRepository;

    @Mock
    ParticipantRepository participantRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    FriendRepository friendRepository;

    @Mock
    ConversationMapper conversationMapper;

    @Mock
    MessageMapper messageMapper;

    @Mock
    SocketPublisher socketPublisher;

    ConversationServiceImpl service;

    @BeforeEach
    void setUp() {
        TestFixtures.authenticateAs(BIG);
        service = new ConversationServiceImpl(
                conversationRepository,
                messageRepository,
                participantRepository,
                userRepository,
                friendRepository,
                conversationMapper,
                messageMapper,
                socketPublisher);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** A GROUP conversation C where the current user (BIG) is a participant with the given role. */
    private ParticipantEntity stubGroupWithMe(ParticipantRole role) {
        ConversationEntity conversation = TestFixtures.conversation(C, ConversationType.GROUP);
        ParticipantEntity me = TestFixtures.participant(conversation, TestFixtures.user(BIG), role);
        when(conversationRepository.findById(C)).thenReturn(Optional.of(conversation));
        when(participantRepository.findActiveParticipant(C, BIG)).thenReturn(Optional.of(me));
        return me;
    }

    /** findConversationsWithDetails(List.of(C)) returns the given conversation and the mapper turns it into DTO. */
    private void stubDetailsAndMapper(ConversationEntity conversation) {
        when(conversationRepository.findConversationsWithDetails(List.of(C))).thenReturn(List.of(conversation));
        when(conversationMapper.toDto(conversation)).thenReturn(DTO);
    }

    // ---------- getConversations ----------

    @Test
    void getConversations_noIds_returnsEmptyPage() {
        when(conversationRepository.findActiveConversationIdsForUser(eq(BIG), isNull(), any(Pageable.class)))
                .thenReturn(List.of());

        BaseResponse<PaginationResponse<ConversationDto>> response = service.getConversations(null, null);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.data().messages()).isEmpty();
        assertThat(response.data().nextCursor()).isNull();
        verify(conversationRepository, never()).findConversationsWithDetails(any());
    }

    @Test
    void getConversations_mapsEachConversationWithItsUnreadCount() {
        ConversationEntity conversation = TestFixtures.conversation(C, ConversationType.GROUP);
        MessageRepository.UnreadCountProjection unread = mock(MessageRepository.UnreadCountProjection.class);
        when(unread.getConversationId()).thenReturn(C);
        when(unread.getUnreadCount()).thenReturn(3L);
        when(conversationRepository.findActiveConversationIdsForUser(
                        eq(BIG), eq(ConversationType.GROUP), any(Pageable.class)))
                .thenReturn(List.of(C));
        when(conversationRepository.findConversationsWithDetails(List.of(C))).thenReturn(List.of(conversation));
        when(messageRepository.countUnreadMessagesByConversation(BIG, List.of(C)))
                .thenReturn(List.of(unread));
        when(conversationMapper.toDto(conversation, 3L)).thenReturn(DTO);

        BaseResponse<PaginationResponse<ConversationDto>> response =
                service.getConversations(null, ConversationType.GROUP);

        assertThat(response.data().messages()).containsExactly(DTO);
        assertThat(response.data().nextCursor()).isNull();
    }

    // ---------- createConversation ----------

    @Test
    void createConversation_unknownType_throwsBadRequest() {
        assertThatThrownBy(() -> service.createConversation(new ConversationRequest(null, "x", List.of(SMALL))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Conversation type is invalid.");
    }

    @Test
    void createDirect_moreThanOneMember_throwsBadRequest() {
        assertThatThrownBy(() -> service.createConversation(
                        new ConversationRequest(ConversationType.DIRECT, null, List.of(SMALL, THIRD))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Direct conversation requires exactly one member.");
    }

    @Test
    void createDirect_withSelf_throwsBadRequest() {
        assertThatThrownBy(() -> service.createConversation(
                        new ConversationRequest(ConversationType.DIRECT, null, List.of(BIG))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot create a direct conversation with yourself.");
    }

    @Test
    void createDirect_existingConversation_restoresParticipantAndReturns201() {
        ConversationEntity conversation = TestFixtures.conversation(C, ConversationType.DIRECT);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(TestFixtures.user(BIG)));
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(TestFixtures.user(SMALL)));
        when(conversationRepository.findDirectConversation(ConversationType.DIRECT, SMALL, BIG))
                .thenReturn(Optional.of(conversation));
        stubDetailsAndMapper(conversation);

        BaseResponse<ConversationDto> response =
                service.createConversation(new ConversationRequest(ConversationType.DIRECT, null, List.of(SMALL)));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.data()).isEqualTo(DTO);
        verify(participantRepository).restoreDeletedParticipant(C, BIG);
        verify(conversationRepository, never()).saveAndFlush(any());
    }

    @Test
    void createDirect_newConversation_ordersDirectUsersAndCreatesTwoParticipants() {
        UserEntity me = TestFixtures.user(BIG);
        UserEntity other = TestFixtures.user(SMALL);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(me));
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(other));
        when(conversationRepository.findDirectConversation(ConversationType.DIRECT, SMALL, BIG))
                .thenReturn(Optional.empty());
        when(conversationRepository.saveAndFlush(any(ConversationEntity.class))).thenAnswer(invocation -> {
            ConversationEntity conversation = invocation.getArgument(0);
            conversation.setId(C);
            return conversation;
        });
        when(participantRepository.save(any(ParticipantEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationRepository.findConversationsWithDetails(List.of(C)))
                .thenReturn(List.of(TestFixtures.conversation(C, ConversationType.DIRECT)));
        when(conversationMapper.toDto(any(ConversationEntity.class))).thenReturn(DTO);

        BaseResponse<ConversationDto> response =
                service.createConversation(new ConversationRequest(ConversationType.DIRECT, null, List.of(SMALL)));

        assertThat(response.status()).isEqualTo(201);
        ArgumentCaptor<ConversationEntity> saved = ArgumentCaptor.forClass(ConversationEntity.class);
        verify(conversationRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(ConversationType.DIRECT);
        assertThat(saved.getValue().getDirectUserA()).isSameAs(other);
        assertThat(saved.getValue().getDirectUserB()).isSameAs(me);
        assertThat(saved.getValue().getCreatedBy()).isSameAs(me);
        verify(participantRepository, times(2)).save(any(ParticipantEntity.class));
        verify(participantRepository).restoreDeletedParticipant(C, BIG);
    }

    @Test
    void createGroup_blankName_throwsBadRequest() {
        assertThatThrownBy(() -> service.createConversation(
                        new ConversationRequest(ConversationType.GROUP, "  ", List.of(SMALL))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Group name is required.");
    }

    @Test
    void createGroup_missingMember_throwsNotFound() {
        UserEntity me = TestFixtures.user(BIG);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(me));
        when(userRepository.findAllById(Set.of(BIG, SMALL))).thenReturn(List.of(me));

        assertThatThrownBy(() -> service.createConversation(
                        new ConversationRequest(ConversationType.GROUP, "Team", List.of(SMALL))))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("One or more members were not found.");
    }

    @Test
    void createGroup_success_makesCreatorAdminAndOthersMembers() {
        UserEntity me = TestFixtures.user(BIG);
        UserEntity other = TestFixtures.user(SMALL);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(me));
        when(userRepository.findAllById(Set.of(BIG, SMALL))).thenReturn(List.of(me, other));
        when(conversationRepository.saveAndFlush(any(ConversationEntity.class))).thenAnswer(invocation -> {
            ConversationEntity conversation = invocation.getArgument(0);
            conversation.setId(C);
            return conversation;
        });
        when(participantRepository.save(any(ParticipantEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationMapper.toDto(any(ConversationEntity.class))).thenReturn(DTO);

        BaseResponse<ConversationDto> response =
                service.createConversation(new ConversationRequest(ConversationType.GROUP, " Team ", List.of(SMALL)));

        assertThat(response.status()).isEqualTo(201);
        ArgumentCaptor<ConversationEntity> saved = ArgumentCaptor.forClass(ConversationEntity.class);
        verify(conversationRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getGroupName()).isEqualTo("Team");
        ArgumentCaptor<ParticipantEntity> participants = ArgumentCaptor.forClass(ParticipantEntity.class);
        verify(participantRepository, times(2)).save(participants.capture());
        assertThat(participants.getAllValues())
                .extracting(p -> p.getUser().getId(), ParticipantEntity::getRole)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(BIG, ParticipantRole.ADMIN),
                        org.assertj.core.groups.Tuple.tuple(SMALL, ParticipantRole.MEMBER));
    }

    // ---------- findOrCreateDirectConversation ----------

    @Test
    void findOrCreateDirectConversation_missingOtherUser_throwsNotFound() {
        when(userRepository.findById(BIG)).thenReturn(Optional.of(TestFixtures.user(BIG)));
        when(userRepository.findById(SMALL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOrCreateDirectConversation(BIG, SMALL))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found.");
    }

    @Test
    void findOrCreateDirectConversation_existing_returnsItWithoutSaving() {
        ConversationEntity conversation = TestFixtures.conversation(C, ConversationType.DIRECT);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(TestFixtures.user(BIG)));
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(TestFixtures.user(SMALL)));
        when(conversationRepository.findDirectConversation(ConversationType.DIRECT, SMALL, BIG))
                .thenReturn(Optional.of(conversation));

        assertThat(service.findOrCreateDirectConversation(BIG, SMALL)).isSameAs(conversation);
        verify(conversationRepository, never()).saveAndFlush(any());
    }

    // ---------- getMessages ----------

    @Test
    void getMessages_unknownConversation_throwsNotFound() {
        when(conversationRepository.existsById(C)).thenReturn(false);

        assertThatThrownBy(() -> service.getMessages(C, null)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getMessages_notParticipant_throwsForbidden() {
        when(conversationRepository.existsById(C)).thenReturn(true);
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(C, BIG))
                .thenReturn(false);

        assertThatThrownBy(() -> service.getMessages(C, null)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getMessages_returnsOldestFirst() {
        ConversationEntity conversation = TestFixtures.conversation(C, ConversationType.GROUP);
        UserEntity me = TestFixtures.user(BIG);
        MessageEntity newer = TestFixtures.message(UUID.randomUUID(), conversation, me);
        MessageEntity older = TestFixtures.message(UUID.randomUUID(), conversation, me);
        older.setCreatedAt(TestFixtures.FIXED_TIME.minusSeconds(60));
        MessageDto newerDto = new MessageDto(newer.getId(), C, BIG, "n", null, MessageType.TEXT, null, null);
        MessageDto olderDto = new MessageDto(older.getId(), C, BIG, "o", null, MessageType.TEXT, null, null);
        when(conversationRepository.existsById(C)).thenReturn(true);
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(C, BIG))
                .thenReturn(true);
        when(messageRepository.findLatestMessages(eq(C), any(Pageable.class))).thenReturn(List.of(newer, older));
        when(messageMapper.toDto(newer)).thenReturn(newerDto);
        when(messageMapper.toDto(older)).thenReturn(olderDto);

        BaseResponse<PaginationResponse<MessageDto>> response = service.getMessages(C, null);

        assertThat(response.data().messages()).containsExactly(olderDto, newerDto);
        assertThat(response.data().nextCursor()).isNull();
    }

    // ---------- markAsSeen ----------

    @Test
    void markAsSeen_unknownConversation_throwsNotFound() {
        when(conversationRepository.findById(C)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsSeen(C)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void markAsSeen_notParticipant_throwsForbidden() {
        when(conversationRepository.findById(C))
                .thenReturn(Optional.of(TestFixtures.conversation(C, ConversationType.GROUP)));
        when(participantRepository.findActiveParticipant(C, BIG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsSeen(C)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void markAsSeen_noMessages_returns200WithoutSaving() {
        stubGroupWithMe(ParticipantRole.MEMBER);

        BaseResponse<Void> response = service.markAsSeen(C);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.message()).isEqualTo("No messages to mark as seen.");
        verify(participantRepository, never()).save(any());
    }

    @Test
    void markAsSeen_alreadyRead_returns200WithoutSaving() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.MEMBER);
        MessageEntity last = TestFixtures.message(UUID.randomUUID(), me.getConversation(), TestFixtures.user(SMALL));
        me.getConversation().setLastMessage(last);
        me.setLastReadMessage(last);

        BaseResponse<Void> response = service.markAsSeen(C);

        assertThat(response.message()).isEqualTo("Messages already marked as seen.");
        verify(participantRepository, never()).save(any());
    }

    @Test
    void markAsSeen_success_updatesParticipantAndPublishesSeenEvent() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.MEMBER);
        MessageEntity last = TestFixtures.message(UUID.randomUUID(), me.getConversation(), TestFixtures.user(SMALL));
        me.getConversation().setLastMessage(last);
        MessageDto lastDto = new MessageDto(last.getId(), C, SMALL, "hello", null, MessageType.TEXT, null, null);
        when(messageMapper.toDto(last)).thenReturn(lastDto);

        BaseResponse<Void> response = service.markAsSeen(C);

        assertThat(response.status()).isEqualTo(200);
        assertThat(me.getLastReadMessage()).isSameAs(last);
        assertThat(me.getLastReadAt()).isNotNull();
        verify(participantRepository).save(me);
        verify(socketPublisher)
                .publishConversationSeenAfterCommit(
                        eq(C), eq(BIG), eq(lastDto), eq(TestFixtures.FIXED_TIME), any(Instant.class));
    }

    // ---------- deleteGroup ----------

    @Test
    void deleteGroup_directConversation_throwsNotFound() {
        when(conversationRepository.findById(C))
                .thenReturn(Optional.of(TestFixtures.conversation(C, ConversationType.DIRECT)));

        assertThatThrownBy(() -> service.deleteGroup(C))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Group conversation not found.");
    }

    @Test
    void deleteGroup_member_throwsForbidden() {
        stubGroupWithMe(ParticipantRole.MEMBER);

        assertThatThrownBy(() -> service.deleteGroup(C))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only admins can manage this group.");
    }

    @Test
    void deleteGroup_admin_softDeletesOwnParticipationAndPublishes() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.ADMIN);
        MessageEntity last = TestFixtures.message(UUID.randomUUID(), me.getConversation(), TestFixtures.user(SMALL));
        me.getConversation().setLastMessage(last);

        BaseResponse<Void> response = service.deleteGroup(C);

        assertThat(response.status()).isEqualTo(200);
        assertThat(me.getDeletedAt()).isNotNull();
        assertThat(me.getLastReadMessage()).isSameAs(last);
        verify(participantRepository).save(me);
        verify(socketPublisher).publishGroupDeletedAfterCommit(C, BIG);
    }

    // ---------- updateGroup ----------

    @Test
    void updateGroup_blankName_throwsBadRequest() {
        assertThatThrownBy(() -> service.updateGroup(C, new UpdateGroupRequest(" ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Group name is required.");
    }

    @Test
    void updateGroup_admin_trimsNameSavesAndPublishes() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.ADMIN);
        ConversationEntity conversation = me.getConversation();
        stubDetailsAndMapper(conversation);

        BaseResponse<ConversationDto> response = service.updateGroup(C, new UpdateGroupRequest("  New name "));

        assertThat(response.status()).isEqualTo(200);
        assertThat(conversation.getGroupName()).isEqualTo("New name");
        verify(conversationRepository).save(conversation);
        verify(socketPublisher).publishConversationUpdatedAfterCommit(C, null, TestFixtures.FIXED_TIME);
    }

    // ---------- addGroupMembers ----------

    @Test
    void addGroupMembers_emptyIds_throwsBadRequest() {
        assertThatThrownBy(() -> service.addGroupMembers(C, new GroupMembersRequest(List.of())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Member ids are required.");
    }

    @Test
    void addGroupMembers_onlySelf_returnsCurrentStateWithoutChanges() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.MEMBER);
        stubDetailsAndMapper(me.getConversation());

        BaseResponse<ConversationDto> response = service.addGroupMembers(C, new GroupMembersRequest(List.of(BIG)));

        assertThat(response.message()).isEqualTo("Members already in group.");
        verify(friendRepository, never()).existsFriendship(any(), any());
    }

    @Test
    void addGroupMembers_notFriend_throwsFriendPermissionWithOffendingIds() {
        stubGroupWithMe(ParticipantRole.MEMBER);
        when(friendRepository.existsFriendship(BIG, SMALL)).thenReturn(false);

        assertThatThrownBy(() -> service.addGroupMembers(C, new GroupMembersRequest(List.of(SMALL))))
                .isInstanceOfSatisfying(
                        FriendPermissionException.class,
                        ex -> assertThat(ex.getNotFriends()).containsExactly(SMALL));
    }

    @Test
    void addGroupMembers_unknownUser_throwsNotFound() {
        stubGroupWithMe(ParticipantRole.MEMBER);
        when(friendRepository.existsFriendship(BIG, SMALL)).thenReturn(true);
        when(userRepository.findAllById(Set.of(SMALL))).thenReturn(List.of());

        assertThatThrownBy(() -> service.addGroupMembers(C, new GroupMembersRequest(List.of(SMALL))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addGroupMembers_newMember_createsParticipantAndPublishes() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.MEMBER);
        UserEntity other = TestFixtures.user(SMALL);
        when(friendRepository.existsFriendship(BIG, SMALL)).thenReturn(true);
        when(userRepository.findAllById(Set.of(SMALL))).thenReturn(List.of(other));
        when(participantRepository.findByConversationIdAndIdUserIdIn(C, Set.of(SMALL)))
                .thenReturn(List.of());
        when(participantRepository.save(any(ParticipantEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubDetailsAndMapper(me.getConversation());

        BaseResponse<ConversationDto> response = service.addGroupMembers(C, new GroupMembersRequest(List.of(SMALL)));

        assertThat(response.status()).isEqualTo(200);
        ArgumentCaptor<ParticipantEntity> saved = ArgumentCaptor.forClass(ParticipantEntity.class);
        verify(participantRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(other);
        assertThat(saved.getValue().getRole()).isEqualTo(ParticipantRole.MEMBER);
        verify(conversationRepository).save(me.getConversation());
        verify(socketPublisher).publishConversationUpdatedAfterCommit(C, null, TestFixtures.FIXED_TIME);
    }

    @Test
    void addGroupMembers_memberWhoLeft_isRejoinedAsMember() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.MEMBER);
        UserEntity other = TestFixtures.user(SMALL);
        ParticipantEntity left = TestFixtures.participant(me.getConversation(), other, ParticipantRole.ADMIN);
        left.setLeftAt(TestFixtures.FIXED_TIME);
        when(friendRepository.existsFriendship(BIG, SMALL)).thenReturn(true);
        when(userRepository.findAllById(Set.of(SMALL))).thenReturn(List.of(other));
        when(participantRepository.findByConversationIdAndIdUserIdIn(C, Set.of(SMALL)))
                .thenReturn(List.of(left));
        stubDetailsAndMapper(me.getConversation());

        service.addGroupMembers(C, new GroupMembersRequest(List.of(SMALL)));

        assertThat(left.getLeftAt()).isNull();
        assertThat(left.getDeletedAt()).isNull();
        assertThat(left.getRole()).isEqualTo(ParticipantRole.MEMBER);
        verify(participantRepository).save(left);
    }

    // ---------- removeGroupMember ----------

    @Test
    void removeGroupMember_nullId_throwsBadRequest() {
        assertThatThrownBy(() -> service.removeGroupMember(C, null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void removeGroupMember_notAdmin_throwsForbidden() {
        stubGroupWithMe(ParticipantRole.MEMBER);

        assertThatThrownBy(() -> service.removeGroupMember(C, SMALL))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only admins can manage group members.");
    }

    @Test
    void removeGroupMember_self_throwsBadRequest() {
        stubGroupWithMe(ParticipantRole.ADMIN);

        assertThatThrownBy(() -> service.removeGroupMember(C, BIG))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot remove yourself. Use leave endpoint instead.");
    }

    @Test
    void removeGroupMember_targetNotInGroup_throwsNotFound() {
        stubGroupWithMe(ParticipantRole.ADMIN);
        when(participantRepository.findActiveParticipant(C, SMALL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeGroupMember(C, SMALL))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Participant not found.");
    }

    @Test
    void removeGroupMember_targetIsAdmin_throwsBadRequest() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.ADMIN);
        ParticipantEntity target =
                TestFixtures.participant(me.getConversation(), TestFixtures.user(SMALL), ParticipantRole.ADMIN);
        when(participantRepository.findActiveParticipant(C, SMALL)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.removeGroupMember(C, SMALL))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot remove an admin from this group.");
    }

    @Test
    void removeGroupMember_success_marksTargetLeftAndPublishes() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.ADMIN);
        ParticipantEntity target =
                TestFixtures.participant(me.getConversation(), TestFixtures.user(SMALL), ParticipantRole.MEMBER);
        when(participantRepository.findActiveParticipant(C, SMALL)).thenReturn(Optional.of(target));
        stubDetailsAndMapper(me.getConversation());

        BaseResponse<ConversationDto> response = service.removeGroupMember(C, SMALL);

        assertThat(response.status()).isEqualTo(200);
        assertThat(target.getLeftAt()).isNotNull();
        verify(participantRepository).save(target);
        verify(socketPublisher).publishConversationUpdatedAfterCommit(C, null, TestFixtures.FIXED_TIME);
    }

    // ---------- leaveGroup ----------

    @Test
    void leaveGroup_lastAdmin_throwsBadRequest() {
        stubGroupWithMe(ParticipantRole.ADMIN);
        when(participantRepository.countActiveByConversationIdAndRoleAndIdUserIdNot(C, ParticipantRole.ADMIN, BIG))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.leaveGroup(C)).isInstanceOf(BadRequestException.class);
        verify(participantRepository, never()).save(any());
    }

    @Test
    void leaveGroup_member_marksLeftAndPublishes() {
        ParticipantEntity me = stubGroupWithMe(ParticipantRole.MEMBER);

        BaseResponse<Void> response = service.leaveGroup(C);

        assertThat(response.status()).isEqualTo(200);
        assertThat(me.getLeftAt()).isNotNull();
        verify(participantRepository).save(me);
        verify(socketPublisher).publishConversationUpdatedAfterCommit(C, null, TestFixtures.FIXED_TIME);
    }
}
