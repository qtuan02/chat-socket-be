package com.chat_socket.service.impl;

import com.chat_socket.dto.AcceptFriendResponse;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.FriendActionRequest;
import com.chat_socket.dto.FriendRequestReceviedDto;
import com.chat_socket.dto.FriendRequestResponse;
import com.chat_socket.dto.FriendRequestSentDto;
import com.chat_socket.dto.FriendSendRequest;
import com.chat_socket.dto.UserDto;
import com.chat_socket.dto.UserPair;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.FriendEntity;
import com.chat_socket.entity.FriendRequestEntity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.enums.FriendRequestStatus;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.mapper.FriendRequestMapper;
import com.chat_socket.mapper.UserMapper;
import com.chat_socket.repository.FriendRepository;
import com.chat_socket.repository.FriendRequestRepository;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.FriendService;
import com.chat_socket.utils.Normalize;
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
    private final FriendRequestMapper friendRequestMapper;
    private final UserMapper userMapper;

    public FriendServiceImpl(
            UserRepository userRepository,
            FriendRepository friendRepository,
            FriendRequestRepository friendRequestRepository,
            FriendRequestMapper friendRequestMapper,
            UserMapper userMapper) {
        this.userRepository = userRepository;
        this.friendRepository = friendRepository;
        this.friendRequestRepository = friendRequestRepository;
        this.friendRequestMapper = friendRequestMapper;
        this.userMapper = userMapper;
    }

    @Override
    public BaseResponse<List<UserDto>> getListFriend() {
        UserSecurity currentUser = Security.getCurrentUser();
        UUID userId = currentUser.id();
        List<FriendEntity> friendships = friendRepository.findFriendshipsOfUser(userId);

        List<UserDto> result = friendships.stream()
                .map(friendship ->
                        friendship.getUserA().getId().equals(userId) ? friendship.getUserB() : friendship.getUserA())
                .map(userMapper::toDto)
                .toList();

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
                sentRequests.stream()
                        .map(f -> new FriendRequestSentDto(
                                f.getId(),
                                f.getFromUser().getId(),
                                new AcceptFriendResponse(
                                        f.getToUser().getId(),
                                        f.getToUser().getFirstName(),
                                        f.getToUser().getLastName(),
                                        f.getToUser().getAvatarUrl()),
                                f.getMessage(),
                                f.getCreatedAt(),
                                f.getUpdatedAt()))
                        .toList(),
                receivedRequests.stream()
                        .map(f -> new FriendRequestReceviedDto(
                                f.getId(),
                                f.getToUser().getId(),
                                new AcceptFriendResponse(
                                        f.getFromUser().getId(),
                                        f.getFromUser().getFirstName(),
                                        f.getFromUser().getLastName(),
                                        f.getToUser().getAvatarUrl()),
                                f.getMessage(),
                                f.getCreatedAt(),
                                f.getUpdatedAt()))
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

        AcceptFriendResponse response =
                new AcceptFriendResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getAvatarUrl());

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
}
