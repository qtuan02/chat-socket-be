package com.chat_socket.entity;

import com.chat_socket.constant.TableName;
import com.chat_socket.utils.UUIDv7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
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
        name = TableName.FRIEND_TABLE,
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_friends",
                        columnNames = {"user_a_id", "user_b_id"}))
public class FriendEntity {
    @Id
    @UUIDv7
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_a_id", nullable = false)
    private UserEntity userA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_b_id", nullable = false)
    private UserEntity userB;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void normalizeUserOrder() {
        if (userA == null || userB == null || userA.getId() == null || userB.getId() == null) {
            return;
        }

        int order = userA.getId().toString().compareTo(userB.getId().toString());
        if (order == 0) {
            throw new IllegalArgumentException("Friend users must be different");
        }

        if (order > 0) {
            UserEntity smallerUser = userB;
            userB = userA;
            userA = smallerUser;
        }
    }
}
