package com.chat_socket.repository;

import com.chat_socket.entity.SessionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {
    void deleteByRefreshToken(String refreshToken);

    Optional<SessionEntity> findByRefreshToken(String refreshToken);
}
