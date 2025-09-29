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

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class FollowServiceImpl implements FollowService {

    FollowRepository followRepository;
    UserRepository userRepository;

    @Override
    @Transactional
    public void follow(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        if (followerId.equals(followeeId)) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }

        
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        User followee = userRepository.findById(followeeId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Idempotent: nếu đã follow thì coi như thành công (không quăng lỗi)
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
            // Trường hợp đua hoặc vi phạm UNIQUE/CHECK ở DB
            log.info("Follow constraint violated ({} -> {}): {}", followerId, followeeId, ex.getMessage());
            // Giữ idempotent: coi như đã follow
            
        }
    }

    @Override
    @Transactional
    public void unfollow(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        try {
            followRepository.deleteByFollower_IdAndFollowee_Id(followerId, followeeId);
            // Idempotent: nếu không tồn tại thì cũng coi như OK
        } catch (Exception ex) {
            log.warn("Unfollow failed ({} -> {}): {}", followerId, followeeId, ex.getMessage());
            throw new AppException(ErrorCode.DATABASE_ERROR);
        }
    }

    @Override
    public boolean isFollowing(Long followerId, Long followeeId) {
        if (followerId == null || followeeId == null) {
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
