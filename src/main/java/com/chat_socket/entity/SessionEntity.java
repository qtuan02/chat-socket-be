package com.chat_socket.entity;

import com.chat_socket.constant.TableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = TableName.SESSION_TABLE)
public class SessionEntity {
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "refresh_token", nullable = false, unique = true, length = 128)
    private String refreshToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
