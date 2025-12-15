package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.NotificationType;
import com.fpt.producerworkbench.dto.request.SendNotificationRequest;
import com.fpt.producerworkbench.dto.response.FollowListResponse;
import com.fpt.producerworkbench.dto.response.FollowResponse;
import com.fpt.producerworkbench.entity.Follow;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.FollowRepository;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.FollowService;
import com.fpt.producerworkbench.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class FollowServiceImpl implements FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    private Long resolveCurrentUserId(Authentication auth) {
        if (auth == null) throw new AppException(ErrorCode.UNAUTHORIZED);

        Object principal = auth.getPrincipal();
        if (principal instanceof User u) {
            if (u.getId() != null) return u.getId();
            if (StringUtils.hasText(u.getEmail())) {
                return userRepository.findByEmail(u.getEmail())
                        .map(User::getId)
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            }
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        String email = auth.getName();
        if (!StringUtils.hasText(email)) throw new AppException(ErrorCode.UNAUTHORIZED);

        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    @Transactional
    public void follow(Authentication auth, Long followeeId) {
        if (followeeId == null) throw new AppException(ErrorCode.BAD_REQUEST);

        Long followerId = resolveCurrentUserId(auth);
        if (followerId.equals(followeeId)) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }

        if (!userRepository.existsById(followeeId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        try {
            Follow follow = Follow.builder()
                    .follower(userRepository.getReferenceById(followerId))
                    .followee(userRepository.getReferenceById(followeeId))
                    .build();
            followRepository.saveAndFlush(follow);
            
            // Gửi notification cho người được follow
            try {
                User follower = userRepository.findById(followerId).orElse(null);
                if (follower != null) {
                    String followerName = follower.getFullName() != null 
                            ? follower.getFullName() 
                            : follower.getEmail();
                    
                    String actionUrl = String.format("/portfolio/user/%d", followerId);
                    
                    notificationService.sendNotification(
                            SendNotificationRequest.builder()
                                    .userId(followeeId)
                                    .type(NotificationType.SYSTEM)
                                    .title("Có người theo dõi bạn")
                                    .message(String.format("%s đã bắt đầu theo dõi bạn.", followerName))
                                    .relatedEntityType(null)
                                    .relatedEntityId(null)
                                    .actionUrl(actionUrl)
                                    .build()
                    );
                }
            } catch (Exception e) {
                log.error("Gặp lỗi khi gửi notification cho follow: {}", e.getMessage());
            }
        } catch (DataIntegrityViolationException ex) {
            log.debug("Follow already exists ({} -> {}): {}", followerId, followeeId, ex.getMessage());
        }
    }

    @Override
    @Transactional
    public void unfollow(Authentication auth, Long followeeId) {
        if (followeeId == null) throw new AppException(ErrorCode.BAD_REQUEST);

        Long followerId = resolveCurrentUserId(auth);
        try {
            followRepository.deleteByFollower_IdAndFollowee_Id(followerId, followeeId);
        } catch (DataAccessException ex) {
            log.warn("Unfollow failed ({} -> {}): {}", followerId, followeeId, ex.getMessage());
            throw new AppException(ErrorCode.DATABASE_ERROR);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFollowing(Authentication auth, Long followeeId) {
        if (followeeId == null) throw new AppException(ErrorCode.BAD_REQUEST);
        Long followerId = resolveCurrentUserId(auth);
        return followRepository.existsByFollower_IdAndFollowee_Id(followerId, followeeId);
    }


    @Override
    @Transactional(readOnly = true)
    public FollowListResponse getFollowingWithTotals(Long userId, Pageable pageable) {
        if (userId == null) throw new AppException(ErrorCode.BAD_REQUEST);
        if (!userRepository.existsById(userId)) throw new AppException(ErrorCode.USER_NOT_FOUND);

        Page<FollowResponse> page = followRepository.findFollowing(userId, pageable);
        long totalFollowers = followRepository.countByFollowee_Id(userId);
        long totalFollowing = followRepository.countByFollower_Id(userId);

        return FollowListResponse.builder()
                .page(page)
                .totalFollowers(totalFollowers)
                .totalFollowing(totalFollowing)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public FollowListResponse getFollowersWithTotals(Long userId, Pageable pageable) {
        if (userId == null) throw new AppException(ErrorCode.BAD_REQUEST);
        if (!userRepository.existsById(userId)) throw new AppException(ErrorCode.USER_NOT_FOUND);

        Page<FollowResponse> page = followRepository.findFollowers(userId, pageable);
        long totalFollowers = followRepository.countByFollowee_Id(userId);
        long totalFollowing = followRepository.countByFollower_Id(userId);

        return FollowListResponse.builder()
                .page(page)
                .totalFollowers(totalFollowers)
                .totalFollowing(totalFollowing)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FollowResponse> getFollowing(Long userId, Pageable pageable) {
        if (userId == null) throw new AppException(ErrorCode.BAD_REQUEST);
        if (!userRepository.existsById(userId)) throw new AppException(ErrorCode.USER_NOT_FOUND);
        return followRepository.findFollowing(userId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FollowResponse> getFollowers(Long userId, Pageable pageable) {
        if (userId == null) throw new AppException(ErrorCode.BAD_REQUEST);
        if (!userRepository.existsById(userId)) throw new AppException(ErrorCode.USER_NOT_FOUND);
        return followRepository.findFollowers(userId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public long countFollowing(Long userId) {
        if (userId == null) throw new AppException(ErrorCode.BAD_REQUEST);
        return followRepository.countByFollower_Id(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countFollowers(Long userId) {
        if (userId == null) throw new AppException(ErrorCode.BAD_REQUEST);
        return followRepository.countByFollowee_Id(userId);
    }
}
