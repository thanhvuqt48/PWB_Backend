package com.fpt.producerworkbench.repository.userguide;

import com.fpt.producerworkbench.entity.userguide   .GuideCategory;
import com.fpt.producerworkbench.entity.userguide.GuideDifficulty;
import com.fpt.producerworkbench.entity.userguide.UserGuide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for UserGuide entity
 * Connected to PostgreSQL datasource
 */
@Repository
public interface UserGuideRepository extends JpaRepository<UserGuide, Long> {
    
    /**
     * Find guide by Pinecone vector ID
     */
    Optional<UserGuide> findByPineconeVectorId(String pineconeVectorId);
    
    /**
     * Find all active guides
     */
    List<UserGuide> findByIsActiveTrue();
    
    /**
     * Find guides by category
     */
    List<UserGuide> findByCategoryAndIsActiveTrue(GuideCategory category);
    
    /**
     * Find guides by difficulty
     */
    List<UserGuide> findByDifficultyAndIsActiveTrue(GuideDifficulty difficulty);
    
    /**
     * Find guides by category and difficulty
     */
    List<UserGuide> findByCategoryAndDifficultyAndIsActiveTrue(
            GuideCategory category, 
            GuideDifficulty difficulty);
    
    /**
     * Search guides by tags (contains any) - Native PostgreSQL query
     */
    @Query(value = "SELECT DISTINCT * FROM user_guides g WHERE " +
                   "g.is_active = true AND " +
                   "EXISTS (SELECT 1 FROM unnest(g.tags) tag WHERE LOWER(tag) LIKE LOWER(CONCAT('%', :tag, '%')))",
           nativeQuery = true)
    List<UserGuide> findByTagsContaining(@Param("tag") String tag);
    
    /**
     * Search guides by keywords (full-text search)
     */
    @Query(value = "SELECT * FROM user_guides WHERE " +
                   "is_active = true AND " +
                   "to_tsvector('english', title || ' ' || content_text) @@ plainto_tsquery('english', :query) " +
                   "ORDER BY created_at DESC",
           nativeQuery = true)
    List<UserGuide> searchByFullText(@Param("query") String query);
    
    /**
     * Get most viewed guides
     */
    List<UserGuide> findTop10ByIsActiveTrueOrderByViewCountDesc();
    
    /**
     * Get most helpful guides
     */
    List<UserGuide> findTop10ByIsActiveTrueOrderByHelpfulCountDesc();
    
    /**
     * Get guides by author
     */
    List<UserGuide> findByAuthorAndIsActiveTrue(String author);
    
    /**
     * Increment view count
     */
    @Modifying
    @Query("UPDATE UserGuide g SET g.viewCount = g.viewCount + 1 WHERE g.id = :id")
    void incrementViewCount(@Param("id") Long id);
    
    /**
     * Increment helpful count
     */
    @Modifying
    @Query("UPDATE UserGuide g SET g.helpfulCount = g.helpfulCount + 1 WHERE g.id = :id")
    void incrementHelpfulCount(@Param("id") Long id);
    
    /**
     * Increment unhelpful count
     */
    @Modifying
    @Query("UPDATE UserGuide g SET g.unhelpfulCount = g.unhelpfulCount + 1 WHERE g.id = :id")
    void incrementUnhelpfulCount(@Param("id") Long id);
    
    /**
     * Count guides by category
     */
    @Query("SELECT g.category, COUNT(g) FROM UserGuide g WHERE g.isActive = true GROUP BY g.category")
    List<Object[]> countByCategory();
    
    /**
     * Count guides by difficulty
     */
    @Query("SELECT g.difficulty, COUNT(g) FROM UserGuide g WHERE g.isActive = true GROUP BY g.difficulty")
    List<Object[]> countByDifficulty();
    
    /**
     * Soft delete (set isActive = false)
     */
    @Modifying
    @Query("UPDATE UserGuide g SET g.isActive = false WHERE g.id = :id")
    void softDelete(@Param("id") Long id);
}
