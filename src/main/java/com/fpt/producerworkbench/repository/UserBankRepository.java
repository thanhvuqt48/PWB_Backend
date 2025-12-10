package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.UserBank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBankRepository extends JpaRepository<UserBank, Long> {
    
    List<UserBank> findByUserId(Long userId);
    
    Optional<UserBank> findByUserIdAndId(Long userId, Long bankAccountId);
    
    List<UserBank> findByUserIdAndIsVerifiedTrue(Long userId);
}

