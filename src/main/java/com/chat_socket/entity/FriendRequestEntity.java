package com.chat_socket.entity;

import com.chat_socket.constant.TableName;
import com.chat_socket.enums.FriendRequestStatus;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = TableName.FRIEND_REQUEST_TABLE,
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_friend_request",
                        columnNames = {"from_user_id", "to_user_id", "status"}),
        indexes = {
            @Index(name = "idx_friend_requests_from_user_id", columnList = "from_user_id"),
            @Index(name = "idx_friend_requests_to_user_id", columnList = "to_user_id"),
            @Index(name = "idx_friend_requests_to_status_created_at", columnList = "to_user_id, status, created_at"),
            @Index(name = "idx_friend_requests_from_status_created_at", columnList = "from_user_id, status, created_at")
        })
public class FriendRequestEntity {
    @Id
    @UUIDv7
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_user_id", nullable = false)
    private UserEntity fromUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_user_id", nullable = false)
    private UserEntity toUser;

    @Column(name = "message", length = 300)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FriendRequestStatus status = FriendRequestStatus.PENDING;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void trimMessage() {
        if (message != null) {
            message = message.trim();
        }
    }
}
