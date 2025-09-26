package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.Follow;
import com.fpt.producerworkbench.dto.response.FollowResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface FollowRepository extends JpaRepository<Follow, Long> {


boolean existsByFollower_IdAndFollowee_Id(Long followerId, Long followeeId);


long countByFollower_Id(Long followerId); // số người mình đang follow
long countByFollowee_Id(Long followeeId); // số follower của mình


void deleteByFollower_IdAndFollowee_Id(Long followerId, Long followeeId);


// Danh sách mình đang follow → trả về User (followee)
@Query("select new com.fpt.producerworkbench.dto.response.FollowResponse(" +
       "u.id, u.firstName, u.lastName, " +
       "concat(coalesce(u.firstName,''),' ',coalesce(u.lastName,'')), " +
       "u.avatarUrl, u.location) " +
       "from Follow f join f.followee u " +
       "where f.follower.id = :followerId")
Page<FollowResponse> findFollowing(@Param("followerId") Long followerId, Pageable pageable);


// Danh sách follower của mình → trả về User (follower)
@Query("select new com.fpt.producerworkbench.dto.response.FollowResponse(" +
       "u.id, u.firstName, u.lastName, " +
       "concat(coalesce(u.firstName,''),' ',coalesce(u.lastName,'')), " +
       "u.avatarUrl, u.location) " +
       "from Follow f join f.follower u " +
       "where f.followee.id = :followeeId")
Page<FollowResponse> findFollowers(@Param("followeeId") Long followeeId, Pageable pageable); 
}
