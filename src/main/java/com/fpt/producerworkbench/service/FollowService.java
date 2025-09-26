package com.fpt.producerworkbench.service;


import com.fpt.producerworkbench.dto.response.FollowResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface FollowService {
void follow(Long followerId, Long followeeId);
void unfollow(Long followerId, Long followeeId);
boolean isFollowing(Long followerId, Long followeeId);


Page<FollowResponse> getFollowing(Long userId, Pageable pageable);
Page<FollowResponse> getFollowers(Long userId, Pageable pageable);


long countFollowing(Long userId);
long countFollowers(Long userId);
}

