package com.chat_socket.service.impl;

import com.chat_socket.dto.AcceptFriendResponse;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.FriendActionRequest;
import com.chat_socket.dto.FriendDto;
import com.chat_socket.dto.FriendRequestResponse;
import com.chat_socket.dto.FriendSendRequest;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.dto.UserPair;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.FriendEntity;
import com.chat_socket.entity.FriendRequestEntity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.enums.FriendRequestStatus;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.mapper.FriendMapper;
import com.chat_socket.mapper.FriendRequestMapper;
import com.chat_socket.repository.FriendRepository;
import com.chat_socket.repository.FriendRequestRepository;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.FriendService;
import com.chat_socket.utils.Normalize;
import com.chat_socket.utils.PaginationUtils;
import com.chat_socket.utils.Security;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendServiceImpl implements FriendService {
    private final UserRepository userRepository;
    private final FriendRepository friendRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final FriendMapper friendMapper;
    private final FriendRequestMapper friendRequestMapper;

    public FriendServiceImpl(
            UserRepository userRepository,
            FriendRepository friendRepository,
            FriendRequestRepository friendRequestRepository,
            FriendMapper friendMapper,
            FriendRequestMapper friendRequestMapper) {
        this.userRepository = userRepository;
        this.friendRepository = friendRepository;
        this.friendRequestRepository = friendRequestRepository;
        this.friendMapper = friendMapper;
        this.friendRequestMapper = friendRequestMapper;
    }

    @Override
    public BaseResponse<PaginationResponse<FriendDto>> getListFriend(PaginationRequest request, String search) {
        UserSecurity currentUser = Security.getCurrentUser();
        UUID userId = currentUser.id();

        PaginationUtils.OffsetPage page = PaginationUtils.resolveOffsetPage(request);
        String usernameSearch = Normalize.normalizeUsernamePattern(search);
        String normalizedNameSearch = Normalize.normalizeTextPattern(search);

        List<FriendEntity> fetchedFriendships = friendRepository.findFriendshipsOfUser(
                userId, usernameSearch, normalizedNameSearch, page.pageRequest());

        PaginationResponse<FriendDto> result = PaginationUtils.toOffsetResponse(
                fetchedFriendships, page, friendship -> friendMapper.toFriendDto(getFriendUser(friendship, userId)));

        return new BaseResponse<>(result, "Success.", HttpStatus.OK.value());
    }

    @Override
    public BaseResponse<FriendRequestResponse> getListFriendRequest() {
        UserSecurity currentUser = Security.getCurrentUser();
        UUID userId = currentUser.id();
        List<FriendRequestEntity> sentRequests =
                friendRequestRepository.findFriendRequestsSentOfUser(userId, FriendRequestStatus.PENDING);
        List<FriendRequestEntity> receivedRequests =
                friendRequestRepository.findFriendRequestsReceivedOfUser(userId, FriendRequestStatus.PENDING);

        FriendRequestResponse result = new FriendRequestResponse(
                sentRequests.stream().map(friendRequestMapper::toSentDto).toList(),
                receivedRequests.stream()
                        .map(friendRequestMapper::toReceivedDto)
                        .toList());

        return new BaseResponse<>(result, "Success.", HttpStatus.OK.value());
    }

    @Override
    @Transactional
    public BaseResponse<String> sendFriendRequest(FriendSendRequest request) {
        UserSecurity currentUser = Security.getCurrentUser();
        UUID fromUserId = currentUser.id();
        UUID toUserId = request.toUserId();

        if (fromUserId.equals(toUserId))
            return new BaseResponse<>(
                    null, "You cannot send a friend request to yourself.", HttpStatus.BAD_REQUEST.value());

        UserEntity fromUser =
                userRepository.findById(fromUserId).orElseThrow(() -> new NotFoundException("User not found."));
        UserEntity toUser =
                userRepository.findById(toUserId).orElseThrow(() -> new NotFoundException("User not found."));

        UserPair pair = Normalize.normalizeUserPair(fromUserId, toUserId);
        if (friendRepository.existsByUserAIdAndUserBId(pair.userAId(), pair.userBId()))
            return new BaseResponse<>(null, "You are already friends.", HttpStatus.CONFLICT.value());

        boolean pendingRequestExists =
                friendRequestRepository.existsBetweenUsersWithStatus(fromUserId, toUserId, FriendRequestStatus.PENDING);
        if (pendingRequestExists)
            return new BaseResponse<>(null, "A pending friend request already exists.", HttpStatus.CONFLICT.value());

        FriendRequestEntity friendRequest = friendRequestMapper.toEntity(request);
        friendRequest.setFromUser(fromUser);
        friendRequest.setToUser(toUser);
        friendRequest.setStatus(FriendRequestStatus.PENDING);
        friendRequestRepository.save(friendRequest);

        return new BaseResponse<>(null, "Friend request sent successfully.", HttpStatus.CREATED.value());
    }

    @Override
    @Transactional
    public BaseResponse<AcceptFriendResponse> acceptFriendRequest(FriendActionRequest request) {
        UserSecurity currentUser = Security.getCurrentUser();
        UUID currentUserId = currentUser.id();
        UUID requestId = request.requestId();

        FriendRequestEntity friendRequest = friendRequestRepository
                .findById(requestId)
                .orElseThrow(() -> new NotFoundException("Friend request not found."));

        if (!friendRequest.getToUser().getId().equals(currentUserId)
                || friendRequest.getStatus() != FriendRequestStatus.PENDING)
            return new BaseResponse<>(
                    null, "You are not authorized to accept this request.", HttpStatus.FORBIDDEN.value());

        FriendEntity friend = new FriendEntity();
        friend.setUserA(friendRequest.getFromUser());
        friend.setUserB(friendRequest.getToUser());
        friendRepository.save(friend);

        friendRequest.setStatus(FriendRequestStatus.ACCEPTED);
        friendRequestRepository.save(friendRequest);

        UserEntity user = userRepository
                .findById(friendRequest.getFromUser().getId())
                .orElseThrow(() -> new NotFoundException("User not found."));

        AcceptFriendResponse response = friendMapper.toAcceptFriendResponse(user);

        return new BaseResponse<>(response, "Friend request accepted successfully.", HttpStatus.CREATED.value());
    }

    @Override
    @Transactional
    public BaseResponse<String> declineFriendRequest(FriendActionRequest request) {
        UserSecurity currentUser = Security.getCurrentUser();
        UUID currentUserId = currentUser.id();
        UUID requestId = request.requestId();

        FriendRequestEntity friendRequest = friendRequestRepository
                .findById(requestId)
                .orElseThrow(() -> new NotFoundException("Friend request not found."));

        if (!friendRequest.getToUser().getId().equals(currentUserId)
                || friendRequest.getStatus() != FriendRequestStatus.PENDING)
            return new BaseResponse<>(
                    null, "You are not authorized to decline this request.", HttpStatus.FORBIDDEN.value());

        friendRequest.setStatus(FriendRequestStatus.REJECTED);
        friendRequestRepository.save(friendRequest);

        return new BaseResponse<>(null, null, HttpStatus.NO_CONTENT.value());
    }

    @Override
    @Transactional
    public BaseResponse<String> cancelFriendRequest(FriendActionRequest request) {
        UserSecurity currentUser = Security.getCurrentUser();
        UUID currentUserId = currentUser.id();
        UUID requestId = request.requestId();

        FriendRequestEntity friendRequest = friendRequestRepository
                .findById(requestId)
                .orElseThrow(() -> new NotFoundException("Friend request not found."));

        if (!friendRequest.getFromUser().getId().equals(currentUserId)
                || friendRequest.getStatus() != FriendRequestStatus.PENDING)
            return new BaseResponse<>(
                    null, "You are not authorized to cancel this request.", HttpStatus.FORBIDDEN.value());

        friendRequestRepository.delete(friendRequest);

        return new BaseResponse<>(null, null, HttpStatus.NO_CONTENT.value());
    }

    @Override
    @Transactional
    public BaseResponse<String> deleteFriend(UUID friendId) {
        UserSecurity currentUser = Security.getCurrentUser();

        if (currentUser.id().equals(friendId))
            return new BaseResponse<>(null, "Friend not found.", HttpStatus.NOT_FOUND.value());

        UserPair pair = Normalize.normalizeUserPair(currentUser.id(), friendId);
        long deleted = friendRepository.deleteByUserAIdAndUserBId(pair.userAId(), pair.userBId());
        if (deleted == 0) return new BaseResponse<>(null, "Friend not found.", HttpStatus.NOT_FOUND.value());

        return new BaseResponse<>(null, null, HttpStatus.NO_CONTENT.value());
    }

    private UserEntity getFriendUser(FriendEntity friendship, UUID currentUserId) {
        return friendship.getUserA().getId().equals(currentUserId) ? friendship.getUserB() : friendship.getUserA();
    }
}
