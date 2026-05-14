package com.chat_socket.repository;

import com.chat_socket.entity.FriendEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendRepository extends JpaRepository<FriendEntity, UUID> {
    boolean existsByUserAIdAndUserBId(UUID userAId, UUID userBId);

    @Query("""
        SELECT f
        FROM FriendEntity f
        WHERE f.userA.id = :userId OR f.userB.id = :userId
        """)
    List<FriendEntity> findFriendshipsOfUser(@Param("userId") UUID userId);
}
