package com.chat_socket.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chat_socket.TestFixtures;
import com.chat_socket.dto.BaseResponse;
import com.chat_socket.dto.MessageDto;
import com.chat_socket.dto.MessageRequest;
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

        assertThatThrownBy(() -> service.sendDirectMessage(new MessageRequest(SMALL, "  ", null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Content is required.");
    }

    @Test
    void sendDirectMessage_noRecipientAndNoConversation_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);

        assertThatThrownBy(() -> service.sendDirectMessage(new MessageRequest(null, "hi", null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Recipient is required.");
    }

    @Test
    void sendDirectMessage_toSelf_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);

        assertThatThrownBy(() -> service.sendDirectMessage(new MessageRequest(BIG, "hi", null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot send a direct message to yourself.");
    }

    @Test
    void sendDirectMessage_conversationIsGroup_throwsNotFound() {
        TestFixtures.authenticateAs(BIG);
        when(conversationRepository.findById(CONVERSATION_ID))
                .thenReturn(Optional.of(TestFixtures.conversation(CONVERSATION_ID, ConversationType.GROUP)));

        assertThatThrownBy(() -> service.sendDirectMessage(new MessageRequest(null, "hi", null, CONVERSATION_ID, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Direct conversation not found.");
    }

    @Test
    void sendDirectMessage_notParticipant_throwsForbidden() {
        TestFixtures.authenticateAs(BIG);
        when(conversationRepository.findById(CONVERSATION_ID))
                .thenReturn(Optional.of(TestFixtures.conversation(CONVERSATION_ID, ConversationType.DIRECT)));
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                        CONVERSATION_ID, BIG))
                .thenReturn(false);

        assertThatThrownBy(() -> service.sendDirectMessage(new MessageRequest(null, "hi", null, CONVERSATION_ID, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void sendDirectMessage_existingConversation_savesMessageUpdatesConversationAndPublishes() {
        TestFixtures.authenticateAs(BIG);
        UserEntity sender = TestFixtures.user(BIG);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.DIRECT);
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNullAndDeletedAtIsNull(
                        CONVERSATION_ID, BIG))
                .thenReturn(true);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(sender));
        MessageDto dto = stubMessagePersistence(conversation, sender);

        BaseResponse<MessageDto> response =
                service.sendDirectMessage(new MessageRequest(null, "hi", null, CONVERSATION_ID, null));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.data()).isEqualTo(dto);
        assertThat(conversation.getLastMessage().getId()).isEqualTo(MESSAGE_ID);
        assertThat(conversation.getLastMessageAt()).isEqualTo(TestFixtures.FIXED_TIME);
        assertThat(conversation.getLastMessage().getType()).isEqualTo(MessageType.TEXT);
        verify(participantRepository).restoreDeletedParticipantsByConversationId(CONVERSATION_ID);
        verify(conversationRepository).save(conversation);
        verify(participantRepository).save(any(ParticipantEntity.class));
        verify(socketPublisher).publishMessageAfterCommit(CONVERSATION_ID, dto, TestFixtures.FIXED_TIME);
    }

    @Test
    void sendDirectMessage_byRecipientWithoutConversation_delegatesToConversationService() {
        TestFixtures.authenticateAs(BIG);
        UserEntity sender = TestFixtures.user(BIG);
        ConversationEntity conversation = TestFixtures.conversation(CONVERSATION_ID, ConversationType.DIRECT);
        when(conversationService.findOrCreateDirectConversation(BIG, SMALL)).thenReturn(conversation);
        when(userRepository.findById(BIG)).thenReturn(Optional.of(sender));
        MessageDto dto = stubMessagePersistence(conversation, sender);

        BaseResponse<MessageDto> response =
                service.sendDirectMessage(new MessageRequest(SMALL, "hi", null, null, null));

        assertThat(response.status()).isEqualTo(201);
        verify(conversationRepository, never()).saveAndFlush(any());
        verify(socketPublisher).publishMessageAfterCommit(CONVERSATION_ID, dto, TestFixtures.FIXED_TIME);
    }

    @Test
    void sendGroupMessage_blankContent_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);

        assertThatThrownBy(() -> service.sendGroupMessage(new MessageRequest(null, "", null, CONVERSATION_ID, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Content is required.");
    }

    @Test
    void sendGroupMessage_noConversation_throwsBadRequest() {
        TestFixtures.authenticateAs(BIG);

        assertThatThrownBy(() -> service.sendGroupMessage(new MessageRequest(null, "hi", null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Conversation is required.");
    }

    @Test
    void sendGroupMessage_conversationIsDirect_throwsNotFound() {
        TestFixtures.authenticateAs(BIG);
        when(conversationRepository.findById(CONVERSATION_ID))
                .thenReturn(Optional.of(TestFixtures.conversation(CONVERSATION_ID, ConversationType.DIRECT)));

        assertThatThrownBy(() -> service.sendGroupMessage(new MessageRequest(null, "hi", null, CONVERSATION_ID, null)))
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
        MessageDto dto = stubMessagePersistence(conversation, sender);

        BaseResponse<MessageDto> response = service.sendGroupMessage(
                new MessageRequest(null, "hi", "http://file", CONVERSATION_ID, MessageType.IMAGE));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.data()).isEqualTo(dto);
        assertThat(conversation.getLastMessage().getType()).isEqualTo(MessageType.IMAGE);
        assertThat(conversation.getLastMessage().getAttachmentUrl()).isEqualTo("http://file");
        verify(socketPublisher).publishMessageAfterCommit(CONVERSATION_ID, dto, TestFixtures.FIXED_TIME);
    }
}
