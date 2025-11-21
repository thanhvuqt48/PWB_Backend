package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.VectorSearchRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.VectorSearchResponse;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.service.VectorDbIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for Vector Database indexing operations
 * Used to populate Pinecone with music terms from JSON file
 */
@Slf4j
@RestController
@RequestMapping("/api/vector-db")
@RequiredArgsConstructor
public class VectorDbIndexingController {
    
    private final VectorDbIndexingService vectorDbIndexingService;
    
    /**
     * Index all music terms from JSON file to Pinecone
     * 
     * Usage:
     * POST http://localhost:8080/api/vector-db/index-all
     * 
     * Response:
     * {
     *   "code": 200,
     *   "message": "Indexing hoàn tất thành công",
     *   "result": {
     *     "totalTerms": 108,
     *     "successCount": 108,
     *     "failedCount": 0,
     *     "durationSeconds": 7
     *   }
     * }
     */
    @PostMapping("/index-all")
    public ApiResponse<Map<String, Object>> indexAllTerms() {
        log.info("📊 Received request to index all music terms");
        
        try {
            Map<String, Object> result = vectorDbIndexingService.indexAllMusicTerms();
            
            // Check if there was an error during indexing
            if (result.containsKey("error")) {
                throw new AppException(ErrorCode.VECTOR_DB_INDEXING_FAILED);
            }
            
            return ApiResponse.<Map<String, Object>>builder()
                    .code(200)
                    .message("Indexing hoàn tất thành công")
                    .result(result)
                    .build();
            
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Indexing failed: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.VECTOR_DB_INDEXING_FAILED);
        }
    }
    
    /**
     * Search for similar music terms using vector similarity
     * 
     * Usage:
     * POST http://localhost:8080/api/vector-db/search
     * {
     *   "query": "Tôi muốn tạo tiếng vang cho giọng hát",
     *   "topK": 5,
     *   "minScore": 0.6
     * }
     */
    @PostMapping("/search")
    public ApiResponse<VectorSearchResponse> search(@RequestBody VectorSearchRequest request) {
        log.info("🔍 Searching for: {}", request.getQuery());
        
        try {
            VectorSearchResponse response = vectorDbIndexingService.searchSimilarTerms(request);
            
            return ApiResponse.<VectorSearchResponse>builder()
                    .code(200)
                    .message("Tìm kiếm thành công")
                    .result(response)
                    .build();
            
        } catch (Exception e) {
            log.error("❌ Search failed: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.VECTOR_DB_SEARCH_FAILED);
        }
    }
    
    /**
     * Get Pinecone vector database statistics
     * 
     * Usage:
     * GET http://localhost:8080/api/vector-db/stats
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        log.info("📊 Getting index statistics");
        
        try {
            Map<String, Object> stats = vectorDbIndexingService.getIndexStats();
            
            return ApiResponse.<Map<String, Object>>builder()
                    .code(200)
                    .message("Lấy thống kê thành công")
                    .result(stats)
                    .build();
            
        } catch (Exception e) {
            log.error("❌ Failed to get stats: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.VECTOR_DB_CONNECTION_FAILED);
        }
    }
    
    /**
     * Delete all vectors (for re-indexing)
     * 
     * Usage:
     * DELETE http://localhost:8080/api/vector-db/delete-all
     * 
     * ⚠️ WARNING: Xóa toàn bộ dữ liệu trong vector database!
     */
    @DeleteMapping("/delete-all")
    public ApiResponse<String> deleteAll() {
        log.warn("⚠️ Received request to delete all vectors!");
        
        try {
            vectorDbIndexingService.clearAllVectors();
            
            return ApiResponse.<String>builder()
                    .code(200)
                    .message("Đã xóa toàn bộ vectors thành công")
                    .result("All vectors deleted")
                    .build();
            
        } catch (Exception e) {
            log.error("❌ Failed to delete vectors: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.VECTOR_DB_CONNECTION_FAILED);
        }
    }
}
