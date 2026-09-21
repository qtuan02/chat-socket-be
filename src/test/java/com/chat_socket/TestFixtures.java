package com.chat_socket;

import com.chat_socket.dto.UserSecurity;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.entity.FriendEntity;
import com.chat_socket.entity.FriendRequestEntity;
import com.chat_socket.entity.MessageEntity;
import com.chat_socket.entity.ParticipantEntity;
import com.chat_socket.entity.ParticipantIdEntity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.enums.FriendRequestStatus;
import com.chat_socket.enums.ParticipantRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Builders for entities and the security context. Tests that call authenticateAs must clearContext in @AfterEach. */
public final class TestFixtures {
    public static final Instant FIXED_TIME = Instant.parse("2026-01-01T12:00:00Z");

    private TestFixtures() {}

    public static UserSecurity authenticateAs(UUID userId) {
        UserSecurity user = new UserSecurity(userId, "user-" + userId, "First", "Last", userId + "@example.com", null);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        return user;
    }

    public static UserEntity user(UUID id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setFirstName("First");
        user.setLastName("Last");
        user.setEmail(id + "@example.com");
        user.setHashedPassword("hashed");
        return user;
    }

    public static ConversationEntity conversation(UUID id, ConversationType type) {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(id);
        conversation.setType(type);
        conversation.setLastMessageAt(FIXED_TIME);
        return conversation;
    }

    public static ParticipantEntity participant(
            ConversationEntity conversation, UserEntity user, ParticipantRole role) {
        ParticipantEntity participant = new ParticipantEntity();
        participant.setId(new ParticipantIdEntity(conversation.getId(), user.getId()));
        participant.setConversation(conversation);
        participant.setUser(user);
        participant.setRole(role);
        return participant;
    }

    public static MessageEntity message(UUID id, ConversationEntity conversation, UserEntity sender) {
        MessageEntity message = new MessageEntity();
        message.setId(id);
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent("hello");
        message.setCreatedAt(FIXED_TIME);
        return message;
    }

    public static FriendEntity friendship(UserEntity userA, UserEntity userB) {
        FriendEntity friendship = new FriendEntity();
        friendship.setId(UUID.randomUUID());
        friendship.setUserA(userA);
        friendship.setUserB(userB);
        return friendship;
    }

    public static FriendRequestEntity friendRequest(
            UUID id, UserEntity fromUser, UserEntity toUser, FriendRequestStatus status) {
        FriendRequestEntity request = new FriendRequestEntity();
        request.setId(id);
        request.setFromUser(fromUser);
        request.setToUser(toUser);
        request.setStatus(status);
        return request;
    }
}
