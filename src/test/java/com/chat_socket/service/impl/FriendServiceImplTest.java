package com.chat_socket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.AcceptFriendResponse;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.FriendActionRequest;
import com.chat_socket.dto.FriendDto;
import com.chat_socket.dto.FriendRequestReceviedDto;
import com.chat_socket.dto.FriendRequestResponse;
import com.chat_socket.dto.FriendRequestSentDto;
import com.chat_socket.dto.FriendSendRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.entity.FriendEntity;
import com.chat_socket.entity.FriendRequestEntity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.enums.FriendRequestStatus;
import com.chat_socket.exception.BadRequestException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.mapper.FriendMapper;
import com.chat_socket.mapper.FriendRequestMapper;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class FriendServiceImplTest {
    // Fixed ids so that SMALL < BIG as strings; UserPair ordering is deterministic in tests
    private static final UUID SMALL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BIG = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    UserRepository userRepository;

    @Mock
    FriendRepository friendRepository;

    @Mock
    FriendRequestRepository friendRequestRepository;

    @Mock
    FriendMapper friendMapper;

    @Mock
    FriendRequestMapper friendRequestMapper;

    FriendServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FriendServiceImpl(
                userRepository, friendRepository, friendRequestRepository, friendMapper, friendRequestMapper);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getListFriend_mapsTheOtherUserOfEachFriendship() {
        TestFixtures.authenticateAs(BIG);
        UserEntity me = TestFixtures.user(BIG);
        UserEntity friend = TestFixtures.user(SMALL);
        FriendEntity friendship = TestFixtures.friendship(friend, me);
        FriendDto dto = new FriendDto(SMALL, "u", "F", "L", null, null);
        when(friendRepository.findFriendshipsOfUser(eq(BIG), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(List.of(friendship));
        when(friendMapper.toFriendDto(friend)).thenReturn(dto);

        BaseResponse<PaginationResponse<FriendDto>> response = service.getListFriend(null, null);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.data().messages()).containsExactly(dto);
        assertThat(response.data().nextOffset()).isNull();
    }

    @Test
    void getListFriendRequest_splitsSentAndReceived() {
        TestFixtures.authenticateAs(BIG);
        FriendRequestEntity sent = TestFixtures.friendRequest(
                UUID.randomUUID(), TestFixtures.user(BIG), TestFixtures.user(SMALL), FriendRequestStatus.PENDING);
        FriendRequestEntity received = TestFixtures.friendRequest(
                UUID.randomUUID(), TestFixtures.user(SMALL), TestFixtures.user(BIG), FriendRequestStatus.PENDING);
        FriendRequestSentDto sentDto = new FriendRequestSentDto(sent.getId(), BIG, null, null, null, null);
        FriendRequestReceviedDto receivedDto =
                new FriendRequestReceviedDto(received.getId(), BIG, null, null, null, null);
        when(friendRequestRepository.findFriendRequestsSentOfUser(BIG, FriendRequestStatus.PENDING))
                .thenReturn(List.of(sent));
        when(friendRequestRepository.findFriendRequestsReceivedOfUser(BIG, FriendRequestStatus.PENDING))
                .thenReturn(List.of(received));
        when(friendRequestMapper.toSentDto(sent)).thenReturn(sentDto);
        when(friendRequestMapper.toReceivedDto(received)).thenReturn(receivedDto);

        BaseResponse<FriendRequestResponse> response = service.getListFriendRequest();

        assertThat(response.data().sentRequests()).containsExactly(sentDto);
        assertThat(response.data().receivedRequests()).containsExactly(receivedDto);
    }

    @Test
    void sendFriendRequest_toSelf_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);

        assertThatThrownBy(() -> service.sendFriendRequest(new FriendSendRequest(BIG, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot send a friend request to yourself.");
    }

    @Test
    void sendFriendRequest_alreadyFriends_returns409() {
        TestFixtures.authenticateAs(BIG);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(TestFixtures.user(BIG)));
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(TestFixtures.user(SMALL)));
        when(friendRepository.existsFriendship(BIG, SMALL)).thenReturn(true);

        BaseResponse<String> response = service.sendFriendRequest(new FriendSendRequest(SMALL, null));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.message()).isEqualTo("You are already friends.");
    }

    @Test
    void sendFriendRequest_pendingExists_returns409() {
        TestFixtures.authenticateAs(BIG);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(TestFixtures.user(BIG)));
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(TestFixtures.user(SMALL)));
        when(friendRepository.existsFriendship(BIG, SMALL)).thenReturn(false);
        when(friendRequestRepository.existsBetweenUsersWithStatus(BIG, SMALL, FriendRequestStatus.PENDING))
                .thenReturn(true);

        BaseResponse<String> response = service.sendFriendRequest(new FriendSendRequest(SMALL, null));

        assertThat(response.status()).isEqualTo(409);
        verify(friendRequestRepository, never()).save(any());
    }

    @Test
    void sendFriendRequest_success_savesPendingRequestAndReturns201() {
        TestFixtures.authenticateAs(BIG);
        UserEntity from = TestFixtures.user(BIG);
        UserEntity to = TestFixtures.user(SMALL);
        FriendSendRequest request = new FriendSendRequest(SMALL, "hi");
        FriendRequestEntity entity = new FriendRequestEntity();
        when(userRepository.findById(BIG)).thenReturn(Optional.of(from));
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(to));
        when(friendRepository.existsFriendship(BIG, SMALL)).thenReturn(false);
        when(friendRequestRepository.existsBetweenUsersWithStatus(BIG, SMALL, FriendRequestStatus.PENDING))
                .thenReturn(false);
        when(friendRequestMapper.toEntity(request)).thenReturn(entity);

        BaseResponse<String> response = service.sendFriendRequest(request);

        assertThat(response.status()).isEqualTo(201);
        assertThat(entity.getFromUser()).isSameAs(from);
        assertThat(entity.getToUser()).isSameAs(to);
        assertThat(entity.getStatus()).isEqualTo(FriendRequestStatus.PENDING);
        verify(friendRequestRepository).save(entity);
    }

    @Test
    void acceptFriendRequest_unknownRequest_throwsNotFound() {
        TestFixtures.authenticateAs(BIG);
        UUID requestId = UUID.randomUUID();
        when(friendRequestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptFriendRequest(new FriendActionRequest(requestId)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Friend request not found.");
    }

    @Test
    void acceptFriendRequest_notRecipient_returns403() {
        TestFixtures.authenticateAs(BIG);
        FriendRequestEntity request = TestFixtures.friendRequest(
                UUID.randomUUID(), TestFixtures.user(BIG), TestFixtures.user(SMALL), FriendRequestStatus.PENDING);
        when(friendRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        BaseResponse<AcceptFriendResponse> response =
                service.acceptFriendRequest(new FriendActionRequest(request.getId()));

        assertThat(response.status()).isEqualTo(403);
        verify(friendRepository, never()).save(any());
    }

    @Test
    void acceptFriendRequest_success_createsFriendshipMarksAcceptedAndReturns201() {
        TestFixtures.authenticateAs(BIG);
        UserEntity from = TestFixtures.user(SMALL);
        UserEntity to = TestFixtures.user(BIG);
        FriendRequestEntity request =
                TestFixtures.friendRequest(UUID.randomUUID(), from, to, FriendRequestStatus.PENDING);
        AcceptFriendResponse dto = new AcceptFriendResponse(SMALL, "F", "L", null);
        when(friendRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(userRepository.findById(SMALL)).thenReturn(Optional.of(from));
        when(friendMapper.toAcceptFriendResponse(from)).thenReturn(dto);

        BaseResponse<AcceptFriendResponse> response =
                service.acceptFriendRequest(new FriendActionRequest(request.getId()));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.data()).isEqualTo(dto);
        ArgumentCaptor<FriendEntity> captor = ArgumentCaptor.forClass(FriendEntity.class);
        verify(friendRepository).save(captor.capture());
        assertThat(captor.getValue().getUserA()).isSameAs(from);
        assertThat(captor.getValue().getUserB()).isSameAs(to);
        assertThat(request.getStatus()).isEqualTo(FriendRequestStatus.ACCEPTED);
        verify(friendRequestRepository).save(request);
    }

    @Test
    void declineFriendRequest_notRecipient_returns403() {
        TestFixtures.authenticateAs(BIG);
        FriendRequestEntity request = TestFixtures.friendRequest(
                UUID.randomUUID(), TestFixtures.user(BIG), TestFixtures.user(SMALL), FriendRequestStatus.PENDING);
        when(friendRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        BaseResponse<String> response = service.declineFriendRequest(new FriendActionRequest(request.getId()));

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    void declineFriendRequest_success_marksRejectedAndReturns204() {
        TestFixtures.authenticateAs(BIG);
        FriendRequestEntity request = TestFixtures.friendRequest(
                UUID.randomUUID(), TestFixtures.user(SMALL), TestFixtures.user(BIG), FriendRequestStatus.PENDING);
        when(friendRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        BaseResponse<String> response = service.declineFriendRequest(new FriendActionRequest(request.getId()));

        assertThat(response.status()).isEqualTo(204);
        assertThat(request.getStatus()).isEqualTo(FriendRequestStatus.REJECTED);
        verify(friendRequestRepository).save(request);
    }

    @Test
    void cancelFriendRequest_notSender_returns403() {
        TestFixtures.authenticateAs(BIG);
        FriendRequestEntity request = TestFixtures.friendRequest(
                UUID.randomUUID(), TestFixtures.user(SMALL), TestFixtures.user(BIG), FriendRequestStatus.PENDING);
        when(friendRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        BaseResponse<String> response = service.cancelFriendRequest(new FriendActionRequest(request.getId()));

        assertThat(response.status()).isEqualTo(403);
        verify(friendRequestRepository, never()).delete(any());
    }

    @Test
    void cancelFriendRequest_success_deletesAndReturns204() {
        TestFixtures.authenticateAs(BIG);
        FriendRequestEntity request = TestFixtures.friendRequest(
                UUID.randomUUID(), TestFixtures.user(BIG), TestFixtures.user(SMALL), FriendRequestStatus.PENDING);
        when(friendRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        BaseResponse<String> response = service.cancelFriendRequest(new FriendActionRequest(request.getId()));

        assertThat(response.status()).isEqualTo(204);
        verify(friendRequestRepository).delete(request);
    }

    @Test
    void deleteFriend_self_returns404() {
        TestFixtures.authenticateAs(BIG);

        BaseResponse<String> response = service.deleteFriend(BIG);

        assertThat(response.status()).isEqualTo(404);
        verify(friendRepository, never()).deleteByUserAIdAndUserBId(any(), any());
    }

    @Test
    void deleteFriend_nothingDeleted_returns404() {
        TestFixtures.authenticateAs(BIG);
        when(friendRepository.deleteByUserAIdAndUserBId(SMALL, BIG)).thenReturn(0L);

        BaseResponse<String> response = service.deleteFriend(SMALL);

        assertThat(response.status()).isEqualTo(404);
    }

    @Test
    void deleteFriend_deleted_returns204UsingNormalizedPair() {
        TestFixtures.authenticateAs(BIG);
        when(friendRepository.deleteByUserAIdAndUserBId(SMALL, BIG)).thenReturn(1L);

        BaseResponse<String> response = service.deleteFriend(SMALL);

        assertThat(response.status()).isEqualTo(204);
    }
}
