package com.chat_socket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.DirectMessageRequest;
import com.chat_socket.dto.GroupMessageRequest;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.UpdateMessageRequest;
import com.chat_socket.entity.ConversationEntity;
import com.chat_socket.entity.MessageEntity;
import com.chat_socket.entity.ParticipantEntity;
import com.chat_socket.entity.UserEntity;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.enums.MessageType;
import com.chat_socket.enums.ParticipantRole;
import com.chat_socket.exception.BadRequestException;
import com.chat_socket.exception.ForbiddenException;
import com.chat_socket.exception.NotFoundException;
import com.chat_socket.mapper.MessageMapper;
import com.chat_socket.repository.ConversationRepository;
import com.chat_socket.repository.MessageRepository;
import com.chat_socket.repository.ParticipantRepository;
import com.chat_socket.repository.UserRepository;
import com.chat_socket.service.ConversationService;
import com.chat_socket.socket.SocketPublisher;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {
    private static final UUID SMALL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BIG = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");

    @Mock
    ConversationRepository conversationRepository;

    @Mock
    MessageRepository messageRepository;

    @Mock
    ParticipantRepository participantRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    MessageMapper messageMapper;

    @Mock
    SocketPublisher socketPublisher;

    @Mock
    ConversationService conversationService;

    MessageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MessageServiceImpl(
                conversationRepository,
                messageRepository,
                participantRepository,
                userRepository,
                messageMapper,
                socketPublisher,
                conversationService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** Stubs everything createMessage() touches: save message, update conversation, mark sender as read, publish. */
    private MessageDto stubMessagePersistence(ConversationEntity conversation, UserEntity sender) {
        ParticipantEntity senderParticipant = TestFixtures.participant(conversation, sender, ParticipantRole.MEMBER);
        MessageDto dto = new MessageDto(
                MESSAGE_ID, conversation.getId(), sender.getId(), "hello", null, MessageType.TEXT, null, null);
        when(messageRepository.saveAndFlush(any(MessageEntity.class))).thenAnswer(invocation -> {
            MessageEntity message = invocation.getArgument(0);
            message.setId(MESSAGE_ID);
            message.setCreatedAt(TestFixtures.FIXED_TIME);
            return message;
        });
        when(participantRepository.findByIdConversationIdAndIdUserId(conversation.getId(), sender.getId()))
                .thenReturn(Optional.of(senderParticipant));
        when(messageMapper.toDto(any(MessageEntity.class))).thenReturn(dto);
        return dto;
    }

    @Test
    void sendDirectMessage_blankContent_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);

        assertThatThrownBy(() -> service.sendDirectMessage(new DirectMessageRequest(SMALL, "  ", null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Content or attachment is required.");
    }

    @Test
    void sendDirectMessage_toSelf_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);

        assertThatThrownBy(() -> service.sendDirectMessage(new DirectMessageRequest(BIG, "hi", null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot send a direct message to yourself.");
    }

    @Test
    void sendDirectMessage_byRecipientWithoutConversation_delegatesToConversationService() {
        TestFixtures.authenticateAs(BIG);
        UserEntity sender = TestFixtures.user(BIG);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.DIRECT);
        when(conversationService.findOrCreateDirectConversation(BIG, SMALL)).thenReturn(conversation);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(sender));
        when(conversationRepository.findWithDetails(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        MessageDto dto = stubMessagePersistence(conversation, sender);

        BaseResponse<MessageDto> response =
                service.sendDirectMessage(new DirectMessageRequest(SMALL, "hi", null, null));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.data()).isEqualTo(dto);
        assertThat(conversation.getLastMessage().getId()).isEqualTo(MESSAGE_ID);
        assertThat(conversation.getLastMessageAt()).isEqualTo(TestFixtures.FIXED_TIME);
        assertThat(conversation.getLastMessage().getType()).isEqualTo(MessageType.TEXT);
        verify(participantRepository).restoreDeletedParticipantsByConversationId(CONVERSATION_ID);
        verify(conversationRepository).save(conversation);
        verify(participantRepository).save(any(ParticipantEntity.class));
        verify(socketPublisher).publishMessageCreatedAfterCommit(conversation, dto);
    }

    // Ratified in spec: delegating to ConversationService changed this 404's text from
    // "Recipient not found." to "User not found." (same status). Pin the propagation so a future
    // wrapping/rewrapping of the exception here doesn't silently change it again.
    @Test
    void sendDirectMessage_byRecipientWithoutConversation_unknownRecipient_propagatesUserNotFound() {
        TestFixtures.authenticateAs(BIG);
        when(conversationService.findOrCreateDirectConversation(BIG, SMALL))
                .thenThrow(new NotFoundException("User not found."));

        assertThatThrownBy(() -> service.sendDirectMessage(new DirectMessageRequest(SMALL, "hi", null, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found.");
    }

    @Test
    void sendGroupMessage_blankContent_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);

        assertThatThrownBy(() -> service.sendGroupMessage(new GroupMessageRequest(CONVERSATION_ID, "", null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Content or attachment is required.");
    }

    @Test
    void sendGroupMessage_attachmentWithTextType_throwsBadRequest() {
        TestFixtures.authenticateAs(SMALL);

        assertThatThrownBy(() -> service.sendGroupMessage(
                        new GroupMessageRequest(CONVERSATION_ID, null, MessageType.TEXT, "http://file")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Type must be IMAGE or FILE when attaching a file.");
    }

    @Test
    void sendGroupMessage_noAttachmentWithImageType_throwsBadRequest() {
        TestFixtures.authenticateAs(SMALL);

        assertThatThrownBy(() -> service.sendGroupMessage(
                        new GroupMessageRequest(CONVERSATION_ID, "hi", MessageType.IMAGE, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Type must be TEXT without an attachment.");
    }

    @Test
    void sendGroupMessage_blankContentWithoutAttachment_throwsBadRequest() {
        TestFixtures.authenticateAs(SMALL);

        assertThatThrownBy(() -> service.sendGroupMessage(new GroupMessageRequest(CONVERSATION_ID, " ", null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Content or attachment is required.");
    }

    @Test
    void sendGroupMessage_conversationIsDirect_throwsNotFound() {
        TestFixtures.authenticateAs(BIG);
        when(conversationRepository.findById(CONVERSATION_ID))
                .thenReturn(Optional.of(TestFixtures.conversation(CONVERSATION_ID, ConversationType.DIRECT)));

        assertThatThrownBy(() -> service.sendGroupMessage(new GroupMessageRequest(CONVERSATION_ID, "hi", null, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Group conversation not found.");
        verify(messageRepository, never()).saveAndFlush(any());
    }

    @Test
    void sendGroupMessage_success_returns201AndPublishes() {
        TestFixtures.authenticateAs(BIG);
        UserEntity sender = TestFixtures.user(BIG);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP);
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                        CONVERSATION_ID, BIG))
                .thenReturn(true);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(sender));
        when(conversationRepository.findWithDetails(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        MessageDto dto = stubMessagePersistence(conversation, sender);

        BaseResponse<MessageDto> response = service.sendGroupMessage(
                new GroupMessageRequest(CONVERSATION_ID, "hi", MessageType.IMAGE, "http://file"));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.data()).isEqualTo(dto);
        assertThat(conversation.getLastMessage().getType()).isEqualTo(MessageType.IMAGE);
        assertThat(conversation.getLastMessage().getAttachmentUrl()).isEqualTo("http://file");
        verify(socketPublisher).publishMessageCreatedAfterCommit(conversation, dto);
    }

    // ---------- updateMessage ----------

    @Test
    void updateMessage_notSender_throwsForbidden() {
        TestFixtures.authenticateAs(BIG);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP);
        MessageEntity message = TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL));
        when(messageRepository.findByIdAndDeletedFalse(message.getId())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.updateMessage(message.getId(), new UpdateMessageRequest("edited")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You can only edit your own messages.");
    }

    @Test
    void updateMessage_imageMessage_throwsBadRequest() {
        TestFixtures.authenticateAs(SMALL);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP);
        MessageEntity message = TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL));
        message.setType(MessageType.IMAGE);
        when(messageRepository.findByIdAndDeletedFalse(message.getId())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.updateMessage(message.getId(), new UpdateMessageRequest("edited")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only text messages can be edited.");
    }

    @Test
    void updateMessage_success_savesPublishesAndRefreshesConversationWhenLast() {
        TestFixtures.authenticateAs(SMALL);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP);
        MessageEntity message = TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL));
        conversation.setLastMessage(message);
        MessageDto dto =
                new MessageDto(message.getId(), CONVERSATION_ID, SMALL, "edited", null, MessageType.TEXT, null, null);
        when(messageRepository.findByIdAndDeletedFalse(message.getId())).thenReturn(Optional.of(message));
        when(messageRepository.save(message)).thenReturn(message);
        when(messageMapper.toDto(message)).thenReturn(dto);
        when(conversationRepository.findWithDetails(CONVERSATION_ID)).thenReturn(Optional.of(conversation));

        BaseResponse<MessageDto> response =
                service.updateMessage(message.getId(), new UpdateMessageRequest(" edited "));

        assertThat(response.status()).isEqualTo(200);
        assertThat(message.getContent()).isEqualTo("edited");
        verify(socketPublisher).publishMessageUpdatedAfterCommit(CONVERSATION_ID, dto);
        verify(socketPublisher).publishConversationUpdatedAfterCommit(conversation);
    }

    @Test
    void updateMessage_notLast_doesNotRefreshConversation() {
        TestFixtures.authenticateAs(SMALL);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP);
        MessageEntity message = TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL));
        conversation.setLastMessage(TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL)));
        when(messageRepository.findByIdAndDeletedFalse(message.getId())).thenReturn(Optional.of(message));
        when(messageRepository.save(message)).thenReturn(message);
        when(messageMapper.toDto(message))
                .thenReturn(new MessageDto(
                        message.getId(), CONVERSATION_ID, SMALL, "x", null, MessageType.TEXT, null, null));

        service.updateMessage(message.getId(), new UpdateMessageRequest("x"));

        verify(socketPublisher, never()).publishConversationUpdatedAfterCommit(any());
    }

    // ---------- deleteMessage ----------

    @Test
    void deleteMessage_notSender_throwsForbidden() {
        TestFixtures.authenticateAs(BIG);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP);
        MessageEntity message = TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL));
        when(messageRepository.findByIdAndDeletedFalse(message.getId())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.deleteMessage(message.getId()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You can only delete your own messages.");
    }

    @Test
    void deleteMessage_notLast_softDeletesAndPublishesOnlyMessageEvent() {
        TestFixtures.authenticateAs(SMALL);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP);
        MessageEntity message = TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL));
        conversation.setLastMessage(TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL)));
        MessageDto dto =
                new MessageDto(message.getId(), CONVERSATION_ID, SMALL, "hello", null, MessageType.TEXT, null, null);
        when(messageRepository.findByIdAndDeletedFalse(message.getId())).thenReturn(Optional.of(message));
        when(messageRepository.save(message)).thenReturn(message);
        when(messageMapper.toDto(message)).thenReturn(dto);

        BaseResponse<Void> response = service.deleteMessage(message.getId());

        assertThat(response.status()).isEqualTo(204);
        assertThat(message.isDeleted()).isTrue();
        verify(socketPublisher).publishMessageDeletedAfterCommit(CONVERSATION_ID, dto);
        verify(socketPublisher, never()).publishConversationUpdatedAfterCommit(any());
    }

    @Test
    void deleteMessage_last_pointsConversationToPreviousMessage() {
        TestFixtures.authenticateAs(SMALL);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP);
        MessageEntity previous = TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL));
        previous.setCreatedAt(TestFixtures.FIXED_TIME.minusSeconds(60));
        MessageEntity message = TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL));
        conversation.setLastMessage(message);
        when(messageRepository.findByIdAndDeletedFalse(message.getId())).thenReturn(Optional.of(message));
        when(messageRepository.save(message)).thenReturn(message);
        when(messageMapper.toDto(message))
                .thenReturn(new MessageDto(
                        message.getId(), CONVERSATION_ID, SMALL, "hello", null, MessageType.TEXT, null, null));
        when(messageRepository.findTopByConversationIdAndDeletedFalseOrderByCreatedAtDescIdDesc(CONVERSATION_ID))
                .thenReturn(Optional.of(previous));
        when(conversationRepository.findWithDetails(CONVERSATION_ID)).thenReturn(Optional.of(conversation));

        service.deleteMessage(message.getId());

        assertThat(conversation.getLastMessage()).isSameAs(previous);
        assertThat(conversation.getLastMessageAt()).isEqualTo(previous.getCreatedAt());
        verify(conversationRepository).save(conversation);
        verify(socketPublisher).publishConversationUpdatedAfterCommit(conversation);
    }

    @Test
    void deleteMessage_lastAndOnlyMessage_clearsConversationLastMessage() {
        TestFixtures.authenticateAs(SMALL);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP);
        MessageEntity message = TestFixtures.message(UUID.randomUUID(), conversation, TestFixtures.user(SMALL));
        conversation.setLastMessage(message);
        when(messageRepository.findByIdAndDeletedFalse(message.getId())).thenReturn(Optional.of(message));
        when(messageRepository.save(message)).thenReturn(message);
        when(messageMapper.toDto(message))
                .thenReturn(new MessageDto(
                        message.getId(), CONVERSATION_ID, SMALL, "hello", null, MessageType.TEXT, null, null));
        when(messageRepository.findTopByConversationIdAndDeletedFalseOrderByCreatedAtDescIdDesc(CONVERSATION_ID))
                .thenReturn(Optional.empty());
        when(conversationRepository.findWithDetails(CONVERSATION_ID)).thenReturn(Optional.of(conversation));

        service.deleteMessage(message.getId());

        assertThat(conversation.getLastMessage()).isNull();
        assertThat(conversation.getLastMessageAt()).isEqualTo(TestFixtures.FIXED_TIME); // unchanged, keeps list order
        verify(socketPublisher).publishConversationUpdatedAfterCommit(conversation);
    }
}
