package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.entity.LiveSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface LiveSessionRepository extends JpaRepository<LiveSession, String> {

    Optional<LiveSession> findByAgoraChannelName(String agoraChannelName);

    @Query("SELECT s FROM LiveSession s WHERE s.demoScenario IS NOT NULL ORDER BY s.createdAt DESC")
    List<LiveSession> findDemoSessions();

}
