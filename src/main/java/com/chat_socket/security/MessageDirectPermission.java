package com.chat_socket.security;

import com.chat_socket.dto.UserPair;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.FriendPermissionException;
import com.chat_socket.repository.FriendRepository;
import com.chat_socket.utils.Normalize;
import com.chat_socket.utils.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component("messageDirectPermission")
public class MessageDirectPermission {
    private final FriendRepository friendRepository;

    public MessageDirectPermission(FriendRepository friendRepository) {
        this.friendRepository = friendRepository;
    }

    public boolean canSendDirect(UUID recipientId) {
        if (recipientId == null) return true;

        UserSecurity currentUser = Security.getCurrentUser();
        if (!isFriend(currentUser.id(), recipientId)) throw new ForbiddenException("You are not friend with this user");

        return true;
    }

    public boolean canSendDirect(List<UUID> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) throw new IllegalArgumentException("Member ids are required");

        UserSecurity currentUser = Security.getCurrentUser();
        List<UUID> notFriends = new ArrayList<>();

        for (UUID memberId : memberIds) {
            if (memberId == null || !isFriend(currentUser.id(), memberId)) notFriends.add(memberId);
        }

        if (!notFriends.isEmpty())
            throw new FriendPermissionException("You can only add friends to a group.", notFriends);

        return true;
    }

    private boolean isFriend(UUID currentUserId, UUID memberId) {
        UserPair pair = Normalize.normalizeUserPair(currentUserId, memberId);
        return friendRepository.existsByUserAIdAndUserBId(pair.userAId(), pair.userBId());
    }
}
