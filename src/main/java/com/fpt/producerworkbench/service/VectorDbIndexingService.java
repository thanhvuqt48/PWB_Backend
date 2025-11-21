package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.VectorSearchRequest;
import com.fpt.producerworkbench.dto.response.VectorSearchResponse;

import java.util.Map;

/**
 * Service interface for vector database indexing operations
 */
public interface VectorDbIndexingService {
    
    /**
     * Index all music terms from JSON file into Pinecone vector database
     * 
     * @return Map containing indexing statistics (totalTerms, successCount, failedCount, duration)
     */
    Map<String, Object> indexAllMusicTerms();
    
    /**
     * Search for similar music terms using vector similarity
     * 
     * @param request Search parameters (query, topK, minScore, category)
     * @return Search results with matching terms and scores
     */
    VectorSearchResponse searchSimilarTerms(VectorSearchRequest request);
    
    /**
     * Get current index statistics
     * 
     * @return Map containing index metadata and stats
     */
    Map<String, Object> getIndexStats();
    
    /**
     * Clear all vectors from the index
     */
    void clearAllVectors();
}
