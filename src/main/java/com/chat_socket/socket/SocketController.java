package com.chat_socket.socket;

import com.chat_socket.constant.SocketChannel;
import com.chat_socket.dto.TypingEvent;
import com.chat_socket.dto.UserSecurity;
import com.chat_socket.repository.ParticipantRepository;
import com.chat_socket.utils.Security;
import java.security.Principal;
import java.util.Set;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

@Controller
public class SocketController {
    private final UserOnlineRegistry userOnlineRegistry;
    private final ParticipantRepository participantRepository;
    private final SocketEmitter socketEmitter;

    SocketController(
            UserOnlineRegistry userOnlineRegistry,
            ParticipantRepository participantRepository,
            SocketEmitter socketEmitter) {
        this.userOnlineRegistry = userOnlineRegistry;
        this.participantRepository = participantRepository;
        this.socketEmitter = socketEmitter;
    }

    @SubscribeMapping(SocketChannel.ONLINE_USERS)
    public Set<UUID> onlineUsers() {
        return userOnlineRegistry.onlineUserIds();
    }

    /** No "stopped typing": the client expires an indicator a few seconds after the last event. */
    @MessageMapping(SocketChannel.TYPING_MAPPING)
    public void typing(@DestinationVariable UUID conversationId, Principal principal) {
        UserSecurity user = Security.getUserSecurityFromPrincipal(principal);
        if (user == null) return;
        if (!participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                conversationId, user.id())) return;

        socketEmitter.emit(
                SocketChannel.TYPING_TOPIC.formatted(conversationId), TypingEvent.of(conversationId, user.id()));
    }
}
