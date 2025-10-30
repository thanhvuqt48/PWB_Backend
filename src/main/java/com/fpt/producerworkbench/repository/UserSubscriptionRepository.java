package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    @Query("SELECT us FROM UserSubscription us WHERE us.user.id = :userId AND us.current = true")
    Optional<UserSubscription> findCurrentByUserId(@Param("userId") Long userId);

    @Query("SELECT us FROM UserSubscription us WHERE us.autoRenewEnabled = true AND us.endDate <= :now")
    List<UserSubscription> findAutoRenewDue(@Param("now") LocalDateTime now);

    @Query("SELECT us FROM UserSubscription us WHERE us.graceUntil IS NOT NULL AND us.graceUntil <= :now AND us.current = true")
    List<UserSubscription> findGraceEnded(@Param("now") LocalDateTime now);
}


