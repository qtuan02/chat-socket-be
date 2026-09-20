package com.chat_socket.entity;

import com.chat_socket.constant.TableName;
import com.chat_socket.enums.ParticipantRole;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = TableName.PARTICIPANT_TABLE,
        indexes = {
            @Index(name = "idx_participants_user_id", columnList = "user_id"),
            @Index(name = "idx_participants_conversation_id", columnList = "conversation_id"),
            @Index(name = "idx_participants_last_read_message_id", columnList = "last_read_message_id")
        })
public class ParticipantEntity {
    @EmbeddedId
    private ParticipantIdEntity id = new ParticipantIdEntity();

    @MapsId("conversationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ConversationEntity conversation;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private ParticipantRole role = ParticipantRole.MEMBER;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_id")
    private MessageEntity lastReadMessage;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "muted_until")
    private Instant mutedUntil;

    public boolean isActive() {
        return leftAt == null && deletedAt == null;
    }
}
