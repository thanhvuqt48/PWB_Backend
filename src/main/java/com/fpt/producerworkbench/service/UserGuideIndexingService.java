package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.UserGuideIndexRequest;
import com.fpt.producerworkbench.dto.request.UserGuideSearchRequest;
import com.fpt.producerworkbench.dto.request.UserGuideUpdateRequest;
import com.fpt.producerworkbench.dto.response.IndexingResultResponse;
import com.fpt.producerworkbench.dto.response.UserGuideResponse;
import com.fpt.producerworkbench.dto.response.UserGuideSearchResponse;
import com.fpt.producerworkbench.dto.response.UserGuideStatsResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Service interface for User Guide indexing and retrieval
 * Handles CRUD operations, vector search, and Pinecone integration
 */
public interface UserGuideIndexingService {

    /**
     * Index a new user guide with optional images
     * 
     * @param request Guide data including title, steps, metadata
     * @param coverImage Optional cover image file
     * @param stepImages Optional step screenshot images (ordered by step)
     * @return Indexing result with guide ID and Pinecone vector ID
     */
    IndexingResultResponse indexGuide(
            UserGuideIndexRequest request, 
            MultipartFile coverImage,
            List<MultipartFile> stepImages
    );

    /**
     * Update an existing guide
     * 
     * @param guideId Guide ID to update
     * @param request Updated guide data
     * @param coverImage Optional new cover image
     * @param stepImages Optional new step images
     * @return Updated guide response
     */
    UserGuideResponse updateGuide(
            Long guideId,
            UserGuideUpdateRequest request,
            MultipartFile coverImage,
            List<MultipartFile> stepImages
    );

    /**
     * Soft delete a guide (set isActive = false)
     * Also removes from Pinecone index
     * 
     * @param guideId Guide ID to delete
     */
    void deleteGuide(Long guideId);

    /**
     * Hard delete a guide (permanent removal)
     * 
     * @param guideId Guide ID to delete
     */
    void permanentlyDeleteGuide(Long guideId);

    /**
     * Search guides using vector similarity
     * 
     * @param request Search parameters (query, filters, topK, etc.)
     * @return Search results with relevance scores
     */
    UserGuideSearchResponse searchGuides(UserGuideSearchRequest request);

    /**
     * Get guide by ID
     * 
     * @param guideId Guide ID
     * @return Guide details with steps
     */
    UserGuideResponse getGuideById(Long guideId);

    /**
     * Get guide by Pinecone vector ID
     * 
     * @param vectorId Pinecone vector ID
     * @return Guide details
     */
    UserGuideResponse getGuideByVectorId(String vectorId);

    /**
     * Get all guides with optional filters (summary only)
     * 
     * @param category Filter by category (optional)
     * @param difficulty Filter by difficulty (optional)
     * @param isActive Filter by active status (default: true)
     * @return List of guide summaries matching filters
     */
    List<com.fpt.producerworkbench.dto.response.UserGuideSummaryResponse> getAllGuides(String category, String difficulty, Boolean isActive);

    /**
     * Get all active guides
     * 
     * @return List of active guides
     */
    List<UserGuideResponse> getAllActiveGuides();

    /**
     * Get guides by category
     * 
     * @param category Guide category
     * @return List of guides in category
     */
    List<UserGuideResponse> getGuidesByCategory(String category);

    /**
     * Get all available categories
     * 
     * @return List of category names
     */
    List<String> getAllCategories();

    /**
     * Increment view count for a guide
     * 
     * @param guideId Guide ID
     */
    void incrementViewCount(Long guideId);

    /**
     * Increment helpful count for a guide
     * 
     * @param guideId Guide ID
     */
    void incrementHelpfulCount(Long guideId);

    /**
     * Get statistics about guides
     * 
     * @return Stats including counts, engagement metrics, etc.
     */
    UserGuideStatsResponse getIndexStats();

    /**
     * Reindex all active guides to Pinecone
     * Used for maintenance or after Pinecone index reset
     * 
     * @return Map with success/failure counts
     */
    Map<String, Object> reindexAllGuides();

    /**
     * Index multiple guides in batch
     * 
     * @param requests List of guide requests
     * @return Map with indexing results
     */
    Map<String, Object> indexMultipleGuides(List<UserGuideIndexRequest> requests);
}
