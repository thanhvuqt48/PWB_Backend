package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.response.FollowListResponse;
import com.fpt.producerworkbench.dto.response.FollowResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

public interface FollowService {

    void follow(Authentication auth, Long followeeId);
    void unfollow(Authentication auth, Long followeeId);
    boolean isFollowing(Authentication auth, Long followeeId);

    FollowListResponse getFollowingWithTotals(Long userId, Pageable pageable);
    FollowListResponse getFollowersWithTotals(Long userId, Pageable pageable);

    Page<FollowResponse> getFollowing(Long userId, Pageable pageable);
    Page<FollowResponse> getFollowers(Long userId, Pageable pageable);
    long countFollowing(Long userId);
    long countFollowers(Long userId);
}
