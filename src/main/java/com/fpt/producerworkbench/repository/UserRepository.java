package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("""
        SELECT u FROM User u
        WHERE
            (COALESCE(TRIM(:kw), '') = '' OR
             LOWER(u.firstName)   LIKE CONCAT('%', LOWER(:kw), '%') OR
             LOWER(u.lastName)    LIKE CONCAT('%', LOWER(:kw), '%') OR
             LOWER(u.email)       LIKE CONCAT('%', LOWER(:kw), '%') OR
             LOWER(u.phoneNumber) LIKE CONCAT('%', LOWER(:kw), '%') OR
             LOWER(u.location)    LIKE CONCAT('%', LOWER(:kw), '%')
            )
          AND (:role IS NULL OR u.role = :role)
          AND (:status IS NULL OR u.status = :status)
        """)
    Page<User> search(
            @Param("kw") String keyword,
            @Param("role") com.fpt.producerworkbench.common.UserRole role,
            @Param("status") com.fpt.producerworkbench.common.UserStatus status,
            Pageable pageable
    );

    Optional<User> findByEmail(String email);
}