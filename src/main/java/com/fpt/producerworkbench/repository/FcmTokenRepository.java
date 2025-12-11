package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.FcmToken;
import com.fpt.producerworkbench.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for FCM Token management
 */
@Repository
public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    /**
     * Find token by token string
     */
    Optional<FcmToken> findByToken(String token);

    /**
     * Find all active tokens for a user
     */
    List<FcmToken> findByUserAndIsActiveTrue(User user);

    /**
     * Find all active tokens for a user by user ID
     */
    @Query("SELECT f FROM FcmToken f WHERE f.user.id = :userId AND f.isActive = true")
    List<FcmToken> findActiveTokensByUserId(@Param("userId") Long userId);

    /**
     * Find all active tokens for multiple users
     */
    @Query("SELECT f FROM FcmToken f WHERE f.user.id IN :userIds AND f.isActive = true")
    List<FcmToken> findActiveTokensByUserIds(@Param("userIds") List<Long> userIds);

    /**
     * Delete token by token string
     */
    void deleteByToken(String token);

    /**
     * Deactivate token
     */
    @Modifying
    @Query("UPDATE FcmToken f SET f.isActive = false WHERE f.token = :token")
    void deactivateToken(@Param("token") String token);

    /**
     * Deactivate all tokens for a user
     */
    @Modifying
    @Query("UPDATE FcmToken f SET f.isActive = false WHERE f.user.id = :userId")
    void deactivateAllTokensForUser(@Param("userId") Long userId);

    /**
     * Check if token exists
     */
    boolean existsByToken(String token);

    /**
     * Update last used timestamp
     */
    @Modifying
    @Query("UPDATE FcmToken f SET f.lastUsedAt = CURRENT_TIMESTAMP WHERE f.token = :token")
    void updateLastUsed(@Param("token") String token);
}
