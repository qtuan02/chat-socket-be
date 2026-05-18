package com.chat_socket.repository;

import com.chat_socket.entity.FriendEntity;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendRepository extends JpaRepository<FriendEntity, UUID> {
    boolean existsByUserAIdAndUserBId(UUID userAId, UUID userBId);

    long deleteByUserAIdAndUserBId(UUID userAId, UUID userBId);

    @Query("""
        SELECT f
        FROM FriendEntity f
        WHERE f.userA.id = :userId OR f.userB.id = :userId
        """)
    List<FriendEntity> findFriendshipsOfUser(@Param("userId") UUID userId);

    @Query("""
            SELECT f
            FROM FriendEntity f
            JOIN FETCH f.userA
            JOIN FETCH f.userB
            WHERE f.userA.id = :userAId AND f.userB.id = :userBId
            """)
    Optional<FriendEntity> findFriendship(@Param("userAId") UUID userAId, @Param("userBId") UUID userBId);

    @Query("""
            SELECT f
            FROM FriendEntity f
            WHERE (f.userA.id = :userId AND f.userB.id IN :userIds)
                OR (f.userB.id = :userId AND f.userA.id IN :userIds)
            """)
    List<FriendEntity> findFriendshipsBetweenUserAndUsers(
            @Param("userId") UUID userId, @Param("userIds") Collection<UUID> userIds);

    @Query("""
            SELECT f
            FROM FriendEntity f
            JOIN FETCH f.userA userA
            JOIN FETCH f.userB userB
            WHERE (userA.id = :userId OR userB.id = :userId)
                AND (
                    :usernameSearch IS NULL
                    OR (
                        userA.id = :userId
                        AND (
                            LOWER(userB.username) LIKE :usernameSearch ESCAPE '\\'
                            OR userB.normalizedName LIKE :normalizedNameSearch ESCAPE '\\'
                        )
                    )
                    OR (
                        userB.id = :userId
                        AND (
                            LOWER(userA.username) LIKE :usernameSearch ESCAPE '\\'
                            OR userA.normalizedName LIKE :normalizedNameSearch ESCAPE '\\'
                        )
                    )
                )
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<FriendEntity> findFriendshipsOfUser(
            @Param("userId") UUID userId,
            @Param("usernameSearch") String usernameSearch,
            @Param("normalizedNameSearch") String normalizedNameSearch,
            Pageable pageable);

    @Query("""
            SELECT f
            FROM FriendEntity f
            JOIN FETCH f.userA userA
            JOIN FETCH f.userB userB
            WHERE (userA.id = :userId OR userB.id = :userId)
                AND f.createdAt < :cursor
                AND (
                    :usernameSearch IS NULL
                    OR (
                        userA.id = :userId
                        AND (
                            LOWER(userB.username) LIKE :usernameSearch ESCAPE '\\'
                            OR userB.normalizedName LIKE :normalizedNameSearch ESCAPE '\\'
                        )
                    )
                    OR (
                        userB.id = :userId
                        AND (
                            LOWER(userA.username) LIKE :usernameSearch ESCAPE '\\'
                            OR userA.normalizedName LIKE :normalizedNameSearch ESCAPE '\\'
                        )
                    )
                )
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<FriendEntity> findFriendshipsOfUserBeforeCursor(
            @Param("userId") UUID userId,
            @Param("cursor") LocalDateTime cursor,
            @Param("usernameSearch") String usernameSearch,
            @Param("normalizedNameSearch") String normalizedNameSearch,
            Pageable pageable);
}
