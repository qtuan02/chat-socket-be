package com.chat_socket.repository;

import com.chat_socket.entity.FriendRequestEntity;
import com.chat_socket.enums.FriendRequestStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendRequestRepository extends JpaRepository<FriendRequestEntity, UUID> {
    @Query("""
            SELECT COUNT(fr) > 0
            FROM FriendRequestEntity fr
            WHERE fr.status = :status
                AND (
                    (fr.fromUser.id = :fromUserId AND fr.toUser.id = :toUserId)
                    OR (fr.fromUser.id = :toUserId AND fr.toUser.id = :fromUserId)
                )
            """)
    boolean existsBetweenUsersWithStatus(
            @Param("fromUserId") UUID fromUserId,
            @Param("toUserId") UUID toUserId,
            @Param("status") FriendRequestStatus status);

    @Query("""
            SELECT f
            FROM FriendRequestEntity f
            WHERE f.fromUser.id = :userId AND f.status = :status
            """)
    List<FriendRequestEntity> findFriendRequestsSentOfUser(
            @Param("userId") UUID userId, @Param("status") FriendRequestStatus status);

    @Query("""
            SELECT f
            FROM FriendRequestEntity f
            WHERE f.toUser.id = :userId AND f.status = :status
            """)
    List<FriendRequestEntity> findFriendRequestsReceivedOfUser(
            @Param("userId") UUID userId, @Param("status") FriendRequestStatus status);
}
