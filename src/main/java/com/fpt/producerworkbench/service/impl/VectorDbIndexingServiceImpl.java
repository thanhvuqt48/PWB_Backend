package com.fpt.producerworkbench.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.producerworkbench.configuration.PineconeProperties;
import com.fpt.producerworkbench.dto.MusicTermDto;
import com.fpt.producerworkbench.dto.request.VectorSearchRequest;
import com.fpt.producerworkbench.dto.response.VectorSearchResponse;
import com.fpt.producerworkbench.service.VectorDbIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of vector database indexing service
 * Handles indexing music terms into Pinecone vector database
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorDbIndexingServiceImpl implements VectorDbIndexingService {
    
    private final PineconeProperties pineconeProperties;
    private final ObjectMapper objectMapper;
    private final VectorStore vectorStore; // Spring AI's VectorStore interface
    
    private static final String MUSIC_TERMS_JSON_PATH = "Data AI/music-terms.json";
    
    /**
     * Load music terms from JSON file
     */
    private List<MusicTermDto> loadMusicTermsFromFile() throws IOException {
        log.info("Loading music terms from: {}", MUSIC_TERMS_JSON_PATH);
        
        ClassPathResource resource = new ClassPathResource(MUSIC_TERMS_JSON_PATH);
        
        try (InputStream inputStream = resource.getInputStream()) {
            List<MusicTermDto> terms = objectMapper.readValue(
                inputStream, 
                new TypeReference<List<MusicTermDto>>() {}
            );
            
            log.info("Successfully loaded {} music terms from JSON", terms.size());
            return terms;
            
        } catch (IOException e) {
            log.error("Failed to load music terms from file: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Index all music terms from JSON file into Pinecone
     */
    public Map<String, Object> indexAllMusicTerms() {
        log.info("Starting to index all music terms from JSON file...");
        log.info("VectorStore class: {}", vectorStore.getClass().getName());
        
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        int errorCount = 0;
        List<String> errors = new ArrayList<>();
        
        try {
            // Load terms from JSON file
            List<MusicTermDto> musicTerms = loadMusicTermsFromFile();
            log.info("Loaded {} terms, beginning indexing process...", musicTerms.size());
            
            // Convert to Spring AI Documents
            List<Document> documents = new ArrayList<>();
            
            for (MusicTermDto term : musicTerms) {
                try {
                    Document doc = convertToDocument(term);
                    documents.add(doc);
                    successCount++;
                    
                    if (successCount % 10 == 0) {
                        log.info("Prepared {}/{} documents...", successCount, musicTerms.size());
                    }
                    
                } catch (Exception e) {
                    errorCount++;
                    String error = String.format("Failed to prepare '%s': %s", 
                        term.getTerm(), e.getMessage());
                    errors.add(error);
                    log.error(error, e);
                }
            }
            
            // Batch add all documents to Pinecone via Spring AI VectorStore
            if (!documents.isEmpty()) {
                log.info("Adding {} documents to Pinecone vector store...", documents.size());
                vectorStore.add(documents);
                log.info("Successfully added all documents to Pinecone");
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            Map<String, Object> result = new HashMap<>();
            result.put("total_terms", musicTerms.size());
            result.put("success_count", successCount);
            result.put("error_count", errorCount);
            result.put("duration_ms", duration);
            result.put("duration_seconds", duration / 1000.0);
            result.put("index_name", pineconeProperties.getIndexName());
            result.put("namespace", pineconeProperties.getNamespace());
            
            if (!errors.isEmpty()) {
                result.put("errors", errors);
            }
            
            log.info("✅ Indexing completed: {} success, {} errors in {}ms", 
                successCount, errorCount, duration);
            
            return result;
            
        } catch (Exception e) {
            log.error("❌ Fatal error during indexing: {}", e.getMessage(), e);
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            errorResult.put("error_type", e.getClass().getSimpleName());
            errorResult.put("success_count", successCount);
            errorResult.put("error_count", errorCount);
            
            if (!errors.isEmpty()) {
                errorResult.put("errors", errors);
            }
            
            return errorResult;
        }
    }
    
    /**
     * Convert MusicTermDto to Spring AI Document with metadata
     */
    private Document convertToDocument(MusicTermDto termDto) {
        // Create rich text for embedding
        String embeddingText = createEmbeddingText(termDto);
        
        // Create metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("term", termDto.getTerm());
        metadata.put("definition", termDto.getDefinition());
        metadata.put("category", termDto.getCategory());
        
        if (termDto.getSynonyms() != null && !termDto.getSynonyms().isEmpty()) {
            metadata.put("synonyms", String.join(", ", termDto.getSynonyms()));
        }
        
        if (termDto.getExamples() != null && !termDto.getExamples().isEmpty()) {
            metadata.put("examples", String.join("; ", termDto.getExamples()));
        }
        
        metadata.put("indexed_at", LocalDateTime.now().toString());
        
        // Create document ID
        String documentId = createDocumentId(termDto.getTerm());
        
        // Create Spring AI Document
        return new Document(documentId, embeddingText, metadata);
    }
    
    /**
     * Create rich text representation for embedding
     */
    private String createEmbeddingText(MusicTermDto term) {
        StringBuilder text = new StringBuilder();
        
        // Term
        text.append(term.getTerm());
        text.append(": ");
        
        // Definition
        text.append(term.getDefinition());
        
        // Examples
        if (term.getExamples() != null && !term.getExamples().isEmpty()) {
            text.append(" Ví dụ: ");
            text.append(String.join(", ", term.getExamples()));
            text.append(".");
        }
        
        // Synonyms
        if (term.getSynonyms() != null && !term.getSynonyms().isEmpty()) {
            text.append(" Từ đồng nghĩa: ");
            text.append(String.join(", ", term.getSynonyms()));
            text.append(".");
        }
        
        // Category
        if (term.getCategory() != null) {
            text.append(" Thể loại: ");
            text.append(term.getCategory());
            text.append(".");
        }
        
        return text.toString();
    }
    
    /**
     * Create unique document ID
     */
    private String createDocumentId(String term) {
        String normalized = term.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
        
        return "term-" + normalized + "-" + System.currentTimeMillis();
    }
    
    /**
     * Search for similar music terms using vector similarity
     */
    public VectorSearchResponse searchSimilarTerms(VectorSearchRequest request) {
        log.info("Searching for similar terms: query='{}', topK={}", 
            request.getQuery(), request.getTopK());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Use Spring AI's SearchRequest
            SearchRequest searchRequest = SearchRequest.query(request.getQuery())
                .withTopK(request.getTopK())
                .withSimilarityThreshold(
                    request.getMinScore() != null ? request.getMinScore().doubleValue() : 0.0
                );
            
            // Perform similarity search
            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            
            // Convert to response DTOs
            List<VectorSearchResponse.SimilarTerm> results = documents.stream()
                .map(this::convertDocumentToSimilarTerm)
                .collect(Collectors.toList());
            
            long duration = System.currentTimeMillis() - startTime;
            
            return VectorSearchResponse.builder()
                .query(request.getQuery())
                .results(results)
                .totalResults(results.size())
                .searchTimeMs(duration)
                .build();
                
        } catch (Exception e) {
            log.error("Error searching similar terms: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search similar terms", e);
        }
    }
    
    /**
     * Convert Spring AI Document to SimilarTerm DTO
     */
    private VectorSearchResponse.SimilarTerm convertDocumentToSimilarTerm(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();
        
        return VectorSearchResponse.SimilarTerm.builder()
            .term((String) metadata.get("term"))
            .definition((String) metadata.get("definition"))
            .category((String) metadata.get("category"))
            .similarityScore(metadata.get("score") != null ? 
                ((Number) metadata.get("score")).doubleValue() : null)
            .synonyms(metadata.get("synonyms") != null ? 
                Arrays.asList(((String) metadata.get("synonyms")).split(", ")) : null)
            .examples(metadata.get("examples") != null ? 
                Arrays.asList(((String) metadata.get("examples")).split("; ")) : null)
            .build();
    }
    
    /**
     * Get Pinecone index statistics
     */
    public Map<String, Object> getIndexStats() {
        log.info("Retrieving index statistics");
        
        try {
            // Spring AI VectorStore doesn't have a built-in stats method
            // So we'll do a sample query to verify the index is working
            SearchRequest testRequest = SearchRequest.query("test")
                .withTopK(1);
            
            List<Document> testResults = vectorStore.similaritySearch(testRequest);
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("index_name", pineconeProperties.getIndexName());
            stats.put("namespace", pineconeProperties.getNamespace());
            stats.put("dimensions", pineconeProperties.getDimension());
            stats.put("metric", pineconeProperties.getMetric());
            stats.put("status", "operational");
            stats.put("test_query_results", testResults.size());
            stats.put("timestamp", LocalDateTime.now().toString());
            
            return stats;
            
        } catch (Exception e) {
            log.error("Error getting index stats: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get index statistics", e);
        }
    }
    
    /**
     * Clear all vectors from Pinecone index
     * WARNING: This deletes all data!
     */
    public void clearAllVectors() {
        log.warn("⚠️ DANGER: Clearing all vectors from index!");
        
        try {
            // Spring AI VectorStore doesn't expose direct delete-all
            // You would need to delete by IDs or use Pinecone SDK directly
            log.warn("Clear operation not yet implemented with Spring AI VectorStore");
            log.warn("To clear the index, use Pinecone dashboard or SDK directly");
            
            throw new UnsupportedOperationException(
                "Clear all vectors not yet implemented. Use Pinecone dashboard to clear the index."
            );
            
        } catch (Exception e) {
            log.error("Error clearing index: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to clear index", e);
        }
    }
}
