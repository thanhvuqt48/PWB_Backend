package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.dto.response.FollowResponse;
import com.fpt.producerworkbench.entity.Follow;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.FollowRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.FollowService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class FollowServiceImpl implements FollowService {

    FollowRepository followRepository;
    UserRepository userRepository;


    private Long resolveCurrentUserId(Authentication authentication) {
        if (authentication == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String email = null;
        Object principal = authentication.getPrincipal();

        if (principal instanceof Jwt) {
            Jwt jwt = (Jwt) principal;
            email = jwt.getClaimAsString("email");
            if (email == null || email.isBlank()) {
                email = jwt.getClaimAsString("sub");
            }
        }

        else if (principal instanceof UserDetails) {
            email = ((UserDetails) principal).getUsername();
        }

        else if (principal instanceof User) {
            email = ((User) principal).getEmail();
        }

        if (email == null || email.isBlank()) {
            email = authentication.getName();
        }

        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        User me = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return me.getId();
    }

    @Override
    @Transactional
    public void follow(Authentication auth, Long followeeId) {
        Long followerId = resolveCurrentUserId(auth);

        if (followeeId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        if (followerId.equals(followeeId)) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }

        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        User followee = userRepository.findById(followeeId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Idempotent
        if (followRepository.existsByFollower_IdAndFollowee_Id(follower.getId(), followee.getId())) {
            return;
        }

        try {
            Follow follow = Follow.builder()
                    .follower(follower)
                    .followee(followee)
                    .build();
            followRepository.save(follow);
        } catch (DataIntegrityViolationException ex) {
            log.info("Follow constraint violated ({} -> {}): {}", followerId, followeeId, ex.getMessage());
            // giữ idempotent: coi như đã follow
        }
    }

    @Override
    @Transactional
    public void unfollow(Authentication auth, Long followeeId) {
        Long followerId = resolveCurrentUserId(auth);

        if (followeeId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        try {
            followRepository.deleteByFollower_IdAndFollowee_Id(followerId, followeeId);
        } catch (Exception ex) {
            log.warn("Unfollow failed ({} -> {}): {}", followerId, followeeId, ex.getMessage());
            throw new AppException(ErrorCode.DATABASE_ERROR);
        }
    }

    @Override
    public boolean isFollowing(Authentication auth, Long followeeId) {
        Long followerId = resolveCurrentUserId(auth);

        if (followeeId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        return followRepository.existsByFollower_IdAndFollowee_Id(followerId, followeeId);
    }

    @Override
    public Page<FollowResponse> getFollowing(Long userId, Pageable pageable) {
        if (userId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        if (!userRepository.existsById(userId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        return followRepository.findFollowing(userId, pageable);
    }

    @Override
    public Page<FollowResponse> getFollowers(Long userId, Pageable pageable) {
        if (userId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        if (!userRepository.existsById(userId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        return followRepository.findFollowers(userId, pageable);
    }

    @Override
    public long countFollowing(Long userId) {
        if (userId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        return followRepository.countByFollower_Id(userId);
    }

    @Override
    public long countFollowers(Long userId) {
        if (userId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        return followRepository.countByFollowee_Id(userId);
    }
}