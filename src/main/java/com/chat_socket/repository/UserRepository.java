package com.chat_socket.repository;

import com.chat_socket.entity.UserEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, UUID id);

    boolean existsByEmailAndIdNot(String email, UUID id);

    Optional<UserEntity> findByUsername(String username);

    @Query("""
            SELECT u
            FROM UserEntity u
            WHERE LOWER(u.username) LIKE :username
            ORDER BY u.username ASC
            """)
    List<UserEntity> searchUsersByUsername(@Param("username") String username, Pageable pageable);
}
