package com.fpt.producerworkbench.repository.userguide;

import com.fpt.producerworkbench.entity.userguide.GuideStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for GuideStep entity
 * Connected to PostgreSQL datasource
 */
@Repository
public interface GuideStepRepository extends JpaRepository<GuideStep, Long> {
    
    /**
     * Find all steps for a guide, ordered by step_order
     */
    List<GuideStep> findByUserGuideIdOrderByStepOrderAsc(Long guideId);
    
    /**
     * Find a specific step by guide ID and step order
     */
    @Query("SELECT s FROM GuideStep s WHERE s.userGuide.id = :guideId AND s.stepOrder = :stepOrder")
    GuideStep findByGuideIdAndStepOrder(@Param("guideId") Long guideId, 
                                         @Param("stepOrder") Integer stepOrder);
    
    /**
     * Count steps for a guide
     */
    Long countByUserGuideId(Long guideId);
    
    /**
     * Delete all steps for a guide
     */
    @Modifying
    @Query("DELETE FROM GuideStep s WHERE s.userGuide.id = :guideId")
    void deleteByUserGuideId(@Param("guideId") Long guideId);
    
    /**
     * Find steps with screenshots
     */
    @Query("SELECT s FROM GuideStep s WHERE s.userGuide.id = :guideId AND s.screenshotUrl IS NOT NULL")
    List<GuideStep> findStepsWithScreenshots(@Param("guideId") Long guideId);
    
    /**
     * Find steps with videos
     */
    @Query("SELECT s FROM GuideStep s WHERE s.userGuide.id = :guideId AND s.videoUrl IS NOT NULL")
    List<GuideStep> findStepsWithVideos(@Param("guideId") Long guideId);
}
