package com.chat_socket.service.impl;

import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.UpdateUserRequest;
import com.chat_socket.dto.UserInfoDto;
import com.chat_socket.dto.UserPair;
import com.chat_socket.dto.UserProfileDto;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.exception.BadRequestException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.mapper.UserMapper;
import com.chat_socket.repository.FriendRepository;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.UserService;
import com.chat_socket.utils.Normalize;
import com.chat_socket.utils.Security;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final FriendRepository friendRepository;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepository userRepository, FriendRepository friendRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.friendRepository = friendRepository;
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
        if (!currentUser.id().equals(userId)) {
            UserPair pair = Normalize.normalizeUserPair(currentUser.id(), userId);
            if (!friendRepository.existsByUserAIdAndUserBId(pair.userAId(), pair.userBId()))
                throw new NotFoundException("User not found");
        }

        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));

        return new BaseResponse<>(toUserInfoDto(user), "Success.", HttpStatus.OK.value());
    }

    private UserInfoDto toUserInfoDto(UserEntity user) {
        return new UserInfoDto(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getPhone(),
                user.getCreatedAt());
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

    private void updateRequiredText(String value, java.util.function.Consumer<String> setter, String message) {
        if (value == null) return;
        setter.accept(requireText(value, message));
    }

    private void updateNullableText(String value, java.util.function.Consumer<String> setter) {
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
