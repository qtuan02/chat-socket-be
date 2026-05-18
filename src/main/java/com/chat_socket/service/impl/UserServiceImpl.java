package com.chat_socket.service.impl;

import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.PaginationRequest;
import com.chat_socket.dto.PaginationResponse;
import com.chat_socket.dto.UpdateUserRequest;
import com.chat_socket.dto.UserInfoDto;
import com.chat_socket.dto.UserPair;
import com.chat_socket.dto.UserProfileDto;
import com.chat_socket.dto.UserSearchDto;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.FriendEntity;
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
import com.chat_socket.service.UserService;
import com.chat_socket.utils.Normalize;
import com.chat_socket.utils.PaginationUtils;
import com.chat_socket.utils.Security;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final FriendRepository friendRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final UserMapper userMapper;

    public UserServiceImpl(
            UserRepository userRepository,
            FriendRepository friendRepository,
            FriendRequestRepository friendRequestRepository,
            UserMapper userMapper) {
        this.userRepository = userRepository;
        this.friendRepository = friendRepository;
        this.friendRequestRepository = friendRequestRepository;
        this.userMapper = userMapper;
    }

    @Override
    public BaseResponse<UserProfileDto> getUserProfile() {
        UserSecurity currentUser = Security.getCurrentUser();
        UserProfileDto userProfile = userRepository
                .findById(currentUser.id())
                .map(userMapper::toUserProfileDto)
                .orElseThrow(() -> new NotFoundException("User not found"));

        return new BaseResponse<>(userProfile, null, HttpStatus.OK.value());
    }

    @Override
    @Transactional
    public BaseResponse<UserProfileDto> updateUserProfile(UpdateUserRequest request) {
        UserSecurity currentUser = Security.getCurrentUser();
        UserEntity user =
                userRepository.findById(currentUser.id()).orElseThrow(() -> new NotFoundException("User not found"));

        updateUsername(user, request.username());
        updateEmail(user, request.email());
        updateRequiredText(request.firstName(), user::setFirstName, "First name is required");
        updateRequiredText(request.lastName(), user::setLastName, "Last name is required");
        updateNullableText(request.avatarUrl(), user::setAvatarUrl);
        updateNullableText(request.avatarId(), user::setAvatarId);
        updateNullableText(request.bio(), user::setBio);
        updateNullableText(request.phone(), user::setPhone);

        UserProfileDto userProfile = userMapper.toUserProfileDto(userRepository.save(user));
        return new BaseResponse<>(userProfile, "User profile updated successfully.", HttpStatus.OK.value());
    }

    @Override
    public BaseResponse<UserInfoDto> getUserInfo(UUID userId) {
        UserSecurity currentUser = Security.getCurrentUser();
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        FriendRequestEntity pendingRequest = findPendingRequest(currentUser.id(), userId);
        FriendStatus status =
                resolveFriendStatus(user, currentUser.id(), hasFriendship(currentUser.id(), userId), pendingRequest);

        return new BaseResponse<>(userMapper.toUserInfoDto(user, status), "Success.", HttpStatus.OK.value());
    }

    @Override
    public BaseResponse<PaginationResponse<UserSearchDto>> searchUsers(
            PaginationRequest paginationRequest, String search) {
        UserSecurity currentUser = Security.getCurrentUser();
        PaginationUtils.OffsetPage page = PaginationUtils.resolveOffsetPage(paginationRequest);
        String usernameSearch = Normalize.normalizeUsernamePattern(search);
        String normalizedNameSearch = Normalize.normalizeTextPattern(search);
        if (usernameSearch == null && normalizedNameSearch == null)
            return new BaseResponse<>(PaginationResponse.offset(List.of(), null), "Success.", HttpStatus.OK.value());

        List<UserEntity> users = userRepository.searchUsers(usernameSearch, normalizedNameSearch, page.pageRequest());
        if (users.isEmpty())
            return new BaseResponse<>(PaginationResponse.offset(List.of(), null), "Success.", HttpStatus.OK.value());

        List<UserEntity> pageUsers = page.items(users);
        List<UUID> userIds = pageUsers.stream().map(UserEntity::getId).toList();
        Map<UUID, FriendEntity> friendshipsByUserId =
                friendRepository.findFriendshipsBetweenUserAndUsers(currentUser.id(), userIds).stream()
                        .collect(Collectors.toMap(
                                friendship -> getFriendUser(friendship, currentUser.id())
                                        .getId(),
                                Function.identity()));
        Map<UUID, FriendRequestEntity> pendingRequestsByUserId =
                friendRequestRepository
                        .findFriendRequestsBetweenUserAndUsers(currentUser.id(), userIds, FriendRequestStatus.PENDING)
                        .stream()
                        .collect(Collectors.toMap(
                                request -> request.getFromUser().getId().equals(currentUser.id())
                                        ? request.getToUser().getId()
                                        : request.getFromUser().getId(),
                                Function.identity()));

        PaginationResponse<UserSearchDto> result = PaginationUtils.toOffsetResponse(users, page, user -> {
            boolean friendshipExists = friendshipsByUserId.containsKey(user.getId());
            FriendRequestEntity pendingRequest = pendingRequestsByUserId.get(user.getId());
            FriendStatus status = resolveFriendStatus(user, currentUser.id(), friendshipExists, pendingRequest);
            return userMapper.toUserSearchDto(user, status, pendingRequest == null ? null : pendingRequest.getId());
        });

        return new BaseResponse<>(result, "Success.", HttpStatus.OK.value());
    }

    private boolean hasFriendship(UUID currentUserId, UUID userId) {
        if (currentUserId.equals(userId)) return false;

        UserPair pair = Normalize.normalizeUserPair(currentUserId, userId);
        return friendRepository.existsByUserAIdAndUserBId(pair.userAId(), pair.userBId());
    }

    private FriendRequestEntity findPendingRequest(UUID currentUserId, UUID userId) {
        if (currentUserId.equals(userId)) return null;

        return friendRequestRepository
                .findFriendRequestsBetweenUserAndUsers(currentUserId, List.of(userId), FriendRequestStatus.PENDING)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private UserEntity getFriendUser(FriendEntity friendship, UUID currentUserId) {
        return friendship.getUserA().getId().equals(currentUserId) ? friendship.getUserB() : friendship.getUserA();
    }

    private FriendStatus resolveFriendStatus(
            UserEntity user, UUID currentUserId, boolean friendshipExists, FriendRequestEntity pendingRequest) {
        if (user.getId().equals(currentUserId)) return FriendStatus.SELF;
        if (friendshipExists) return FriendStatus.FRIEND;
        if (pendingRequest == null) return FriendStatus.NONE;

        return pendingRequest.getFromUser().getId().equals(currentUserId) ? FriendStatus.SENT : FriendStatus.RECEIVED;
    }

    private void updateUsername(UserEntity user, String username) {
        if (username == null) return;

        String normalizedUsername = requireText(username, "Username is required");
        if (!normalizedUsername.equals(user.getUsername())
                && userRepository.existsByUsernameAndIdNot(normalizedUsername, user.getId()))
            throw new BadRequestException("Username already exists");

        user.setUsername(normalizedUsername);
    }

    private void updateEmail(UserEntity user, String email) {
        if (email == null) return;

        String normalizedEmail = requireText(email, "Email is required");
        if (!normalizedEmail.equals(user.getEmail())
                && userRepository.existsByEmailAndIdNot(normalizedEmail, user.getId()))
            throw new BadRequestException("Email already exists");

        user.setEmail(normalizedEmail);
    }

    private void updateRequiredText(String value, Consumer<String> setter, String message) {
        if (value == null) return;
        setter.accept(requireText(value, message));
    }

    private void updateNullableText(String value, Consumer<String> setter) {
        if (value == null) return;

        String trimmedValue = value.trim();
        setter.accept(trimmedValue.isEmpty() ? null : trimmedValue);
    }

    private String requireText(String value, String message) {
        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty()) throw new BadRequestException(message);

        return trimmedValue;
    }
}
