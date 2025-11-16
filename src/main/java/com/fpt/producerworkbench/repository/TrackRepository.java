package com.fpt.producerworkbench.repository;

import com.fpt.producerworkbench.common.ProcessingStatus;
import com.fpt.producerworkbench.common.TrackStatus;
import com.fpt.producerworkbench.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrackRepository extends JpaRepository<Track, Long> {

    @Query("SELECT t FROM Track t WHERE t.milestone.id = :milestoneId ORDER BY t.createdAt DESC")
    List<Track> findByMilestoneIdOrderByCreatedAtDesc(@Param("milestoneId") Long milestoneId);

    @Query("SELECT t FROM Track t WHERE t.milestone.id = :milestoneId AND t.status = :status ORDER BY t.createdAt DESC")
    List<Track> findByMilestoneIdAndStatus(@Param("milestoneId") Long milestoneId, @Param("status") TrackStatus status);

    @Query("SELECT t FROM Track t WHERE t.milestone.id = :milestoneId AND t.processingStatus = :processingStatus ORDER BY t.createdAt DESC")
    List<Track> findByMilestoneIdAndProcessingStatus(@Param("milestoneId") Long milestoneId, @Param("processingStatus") ProcessingStatus processingStatus);

    @Query("SELECT t FROM Track t WHERE t.user.id = :userId ORDER BY t.createdAt DESC")
    List<Track> findByUserId(@Param("userId") Long userId);

    @Query("SELECT t FROM Track t WHERE t.milestone.contract.project.id = :projectId ORDER BY t.createdAt DESC")
    List<Track> findByProjectId(@Param("projectId") Long projectId);

    @Query("SELECT COUNT(t) > 0 FROM Track t WHERE t.id = :trackId AND t.milestone.id = :milestoneId")
    boolean existsByIdAndMilestoneId(@Param("trackId") Long trackId, @Param("milestoneId") Long milestoneId);

    @Query("SELECT t FROM Track t WHERE t.id = :trackId AND t.milestone.id = :milestoneId")
    Optional<Track> findByIdAndMilestoneId(@Param("trackId") Long trackId, @Param("milestoneId") Long milestoneId);

    @Query("SELECT COUNT(t) FROM Track t WHERE t.milestone.id = :milestoneId")
    long countByMilestoneId(@Param("milestoneId") Long milestoneId);

    @Query("SELECT t FROM Track t WHERE t.processingStatus = :processingStatus")
    List<Track> findByProcessingStatus(@Param("processingStatus") ProcessingStatus processingStatus);
}




