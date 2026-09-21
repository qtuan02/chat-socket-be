package com.chat_socket.entity;

import com.chat_socket.constant.TableName;
import com.chat_socket.enums.ConversationType;
import com.chat_socket.utils.UUIDv7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = TableName.CONVERSATION_TABLE,
        indexes = @Index(name = "idx_conversations_last_message_at", columnList = "last_message_at DESC"))
public class ConversationEntity {
    @Id
    @UUIDv7
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ConversationType type;

    @Column(name = "group_name", length = 255)
    private String groupName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "direct_user_a_id")
    private UserEntity directUserA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "direct_user_b_id")
    private UserEntity directUserB;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_message_id")
    private MessageEntity lastMessage;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    /** Only active participants — a kicked/left member must not reappear in a loaded conversation's roster. */
    @OneToMany(mappedBy = "conversation")
    @SQLRestriction("left_at IS NULL AND deleted_at IS NULL")
    private List<ParticipantEntity> participants = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    private void normalizeDirectUsers() {
        if (type != ConversationType.DIRECT || directUserA == null || directUserB == null) {
            return;
        }

        int order = directUserA.getId().toString().compareTo(directUserB.getId().toString());
        if (order == 0) {
            throw new IllegalArgumentException("Direct conversation users must be different");
        }

        if (order > 0) {
            UserEntity user = directUserB;
            directUserB = directUserA;
            directUserA = user;
        }
    }
}
