package com.chat_socket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.dto.UpdateUserRequest;
import com.chat_socket.dto.UserProfileDto;
import com.chat_socket.dto.UserSearchDto;
import com.chat_socket.entity.FriendRequestEntity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.enums.FriendRequestStatus;
import com.chat_socket.enums.FriendStatus;
import com.chat_socket.exception.BadRequestException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.mapper.UserMapper;
import com.chat_socket.repository.FriendRepository;
import com.chat_socket.repository.FriendRequestRepository;
import com.chat_socket.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {
    private static final UUID SMALL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BIG = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UpdateUserRequest EMPTY_UPDATE =
            new UpdateUserRequest(null, null, null, null, null, null, null, null);

    @Mock
    UserRepository userRepository;

    @Mock
    FriendRepository friendRepository;

    @Mock
    FriendRequestRepository friendRequestRepository;

    @Mock
    UserMapper userMapper;

    UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, friendRepository, friendRequestRepository, userMapper);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getUserProfile_unknownUser_throwsNotFound() {
        TestFixtures.authenticateAs(BIG);
        when(userRepository.findById(BIG)).thenReturn(Optional.empty());

        assertThatThrownBy(service::getUserProfile).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getUserProfile_returnsMappedProfile() {
        TestFixtures.authenticateAs(BIG);
        UserEntity user = TestFixtures.user(BIG);
        UserProfileDto dto = new UserProfileDto(BIG, "u", "F", "L", "e", null, null, null);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(user));
        when(userMapper.toUserProfileDto(user)).thenReturn(dto);

        BaseResponse<UserProfileDto> response = service.getUserProfile();

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.data()).isEqualTo(dto);
    }

    @Test
    void updateUserProfile_usernameTaken_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);
        UserEntity user = TestFixtures.user(BIG);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsernameAndIdNot("taken", BIG)).thenReturn(true);

        assertThatThrownBy(() -> service.updateUserProfile(
                        new UpdateUserRequest(" taken ", null, null, null, null, null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Username already exists");
    }

    @Test
    void updateUserProfile_blankFirstName_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(TestFixtures.user(BIG)));

        assertThatThrownBy(() -> service.updateUserProfile(
                        new UpdateUserRequest(null, null, "   ", null, null, null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("First name is required");
    }

    @Test
    void updateUserProfile_trimsTextAndBlankOptionalBecomesNull() {
        TestFixtures.authenticateAs(BIG);
        UserEntity user = TestFixtures.user(BIG);
        user.setBio("old bio");
        UserProfileDto dto = new UserProfileDto(BIG, "u", "F", "L", "e", null, null, null);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toUserProfileDto(user)).thenReturn(dto);

        BaseResponse<UserProfileDto> response = service.updateUserProfile(
                new UpdateUserRequest(null, null, " Anna ", null, null, null, "   ", " 0123 "));

        assertThat(response.status()).isEqualTo(200);
        assertThat(user.getFirstName()).isEqualTo("Anna");
        assertThat(user.getBio()).isNull();
        assertThat(user.getPhone()).isEqualTo("0123");
        assertThat(user.getUsername()).isEqualTo("user-" + BIG);
    }

    @Test
    void updateUserProfile_emptyRequest_savesUnchangedUser() {
        TestFixtures.authenticateAs(BIG);
        UserEntity user = TestFixtures.user(BIG);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toUserProfileDto(user)).thenReturn(null);

        service.updateUserProfile(EMPTY_UPDATE);

        verify(userRepository).save(user);
    }

    @Test
    void getUserInfo_self_returnsStatusSelf() {
        TestFixtures.authenticateAs(BIG);
        UserEntity me = TestFixtures.user(BIG);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(me));

        service.getUserInfo(BIG);

        verify(userMapper).toUserInfoDto(me, FriendStatus.SELF);
    }

    @Test
    void getUserInfo_friend_returnsStatusFriend() {
        TestFixtures.authenticateAs(BIG);
        UserEntity other = TestFixtures.user(SMALL);
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(other));
        when(friendRequestRepository.findFriendRequestsBetweenUserAndUsers(
                        BIG, List.of(SMALL), FriendRequestStatus.PENDING))
                .thenReturn(List.of());
        when(friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(true);

        service.getUserInfo(SMALL);

        verify(userMapper).toUserInfoDto(other, FriendStatus.FRIEND);
    }

    @Test
    void getUserInfo_pendingRequestSentByMe_returnsStatusSent() {
        TestFixtures.authenticateAs(BIG);
        UserEntity other = TestFixtures.user(SMALL);
        FriendRequestEntity pending = TestFixtures.friendRequest(
                UUID.randomUUID(), TestFixtures.user(BIG), other, FriendRequestStatus.PENDING);
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(other));
        when(friendRequestRepository.findFriendRequestsBetweenUserAndUsers(
                        BIG, List.of(SMALL), FriendRequestStatus.PENDING))
                .thenReturn(List.of(pending));
        when(friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(false);

        service.getUserInfo(SMALL);

        verify(userMapper).toUserInfoDto(other, FriendStatus.SENT);
    }

    @Test
    void getUserInfo_pendingRequestReceived_returnsStatusReceived() {
        TestFixtures.authenticateAs(BIG);
        UserEntity other = TestFixtures.user(SMALL);
        FriendRequestEntity pending = TestFixtures.friendRequest(
                UUID.randomUUID(), other, TestFixtures.user(BIG), FriendRequestStatus.PENDING);
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(other));
        when(friendRequestRepository.findFriendRequestsBetweenUserAndUsers(
                        BIG, List.of(SMALL), FriendRequestStatus.PENDING))
                .thenReturn(List.of(pending));
        when(friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(false);

        service.getUserInfo(SMALL);

        verify(userMapper).toUserInfoDto(other, FriendStatus.RECEIVED);
    }

    @Test
    void getUserInfo_noRelation_returnsStatusNone() {
        TestFixtures.authenticateAs(BIG);
        UserEntity other = TestFixtures.user(SMALL);
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(other));
        when(friendRequestRepository.findFriendRequestsBetweenUserAndUsers(
                        BIG, List.of(SMALL), FriendRequestStatus.PENDING))
                .thenReturn(List.of());
        when(friendRepository.existsByUserAIdAndUserBId(SMALL, BIG)).thenReturn(false);

        service.getUserInfo(SMALL);

        verify(userMapper).toUserInfoDto(other, FriendStatus.NONE);
    }

    @Test
    void searchUsers_blankSearch_returnsEmptyWithoutQuerying() {
        TestFixtures.authenticateAs(BIG);

        BaseResponse<PaginationResponse<UserSearchDto>> response = service.searchUsers(null, "   ");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.data().messages()).isEmpty();
        assertThat(response.data().nextOffset()).isNull();
    }

    @Test
    void searchUsers_resolvesStatusAndPendingRequestIdPerUser() {
        TestFixtures.authenticateAs(BIG);
        UserEntity friend = TestFixtures.user(SMALL);
        UUID strangerId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UserEntity stranger = TestFixtures.user(strangerId);
        FriendRequestEntity pending = TestFixtures.friendRequest(
                UUID.randomUUID(), stranger, TestFixtures.user(BIG), FriendRequestStatus.PENDING);
        when(userRepository.searchUsers(eq("%bob%"), eq("%bob%"), any(Pageable.class)))
                .thenReturn(List.of(friend, stranger));
        when(friendRepository.findFriendshipsBetweenUserAndUsers(BIG, List.of(SMALL, strangerId)))
                .thenReturn(List.of(TestFixtures.friendship(friend, TestFixtures.user(BIG))));
        when(friendRequestRepository.findFriendRequestsBetweenUserAndUsers(
                        BIG, List.of(SMALL, strangerId), FriendRequestStatus.PENDING))
                .thenReturn(List.of(pending));
        when(userMapper.toUserSearchDto(any(UserEntity.class), any(FriendStatus.class), any()))
                .thenReturn(null);

        BaseResponse<PaginationResponse<UserSearchDto>> response =
                service.searchUsers(new PaginationRequest(10, null, 0), "bob");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.data().messages()).hasSize(2);
        verify(userMapper).toUserSearchDto(friend, FriendStatus.FRIEND, null);
        verify(userMapper).toUserSearchDto(stranger, FriendStatus.RECEIVED, pending.getId());
    }
}
