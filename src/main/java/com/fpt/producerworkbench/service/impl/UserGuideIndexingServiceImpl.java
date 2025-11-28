package com.fpt.producerworkbench.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.producerworkbench.configuration.GeminiConfig;
import com.fpt.producerworkbench.configuration.PineconeNamespaceConfig;
import com.fpt.producerworkbench.dto.request.UserGuideIndexRequest;
import com.fpt.producerworkbench.dto.request.UserGuideSearchRequest;
import com.fpt.producerworkbench.dto.request.UserGuideUpdateRequest;
import com.fpt.producerworkbench.dto.response.IndexingResultResponse;
import com.fpt.producerworkbench.dto.response.UserGuideResponse;
import com.fpt.producerworkbench.dto.response.UserGuideSearchResponse;
import com.fpt.producerworkbench.dto.response.UserGuideStatsResponse;
import com.fpt.producerworkbench.entity.userguide.GuideCategory;
import com.fpt.producerworkbench.entity.userguide.GuideStep;
import com.fpt.producerworkbench.entity.userguide.UserGuide;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.UserGuideMapper;
import com.fpt.producerworkbench.repository.userguide.GuideStepRepository;
import com.fpt.producerworkbench.repository.userguide.UserGuideRepository;
import com.fpt.producerworkbench.service.FileKeyGenerator;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.UserGuideIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserGuideIndexingServiceImpl implements UserGuideIndexingService {

    private final UserGuideRepository userGuideRepository;
    private final GuideStepRepository guideStepRepository;
    private final FileStorageService fileStorageService;
    private final FileKeyGenerator fileKeyGenerator;
    private final VectorStore vectorStore;
    private final UserGuideMapper userGuideMapper;
    private final GeminiConfig geminiConfig;
    private final PineconeNamespaceConfig pineconeNamespaceConfig;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final jakarta.persistence.EntityManager entityManager;

    @Override
    @Transactional(transactionManager = "userGuideTransactionManager")
    public IndexingResultResponse indexGuide(
            UserGuideIndexRequest request,
            MultipartFile coverImage,
            List<MultipartFile> stepImages) {

        long startTime = System.currentTimeMillis();
        log.info("📝 Indexing new user guide: '{}'", request.getTitle());

        try {
            // Step 1: Upload cover image to S3 (if provided)
            String coverImageUrl = null;
            if (coverImage != null && !coverImage.isEmpty()) {
                String coverImageKey = String.format("guides/cover/%s_%s",
                        System.currentTimeMillis(),
                        coverImage.getOriginalFilename());
                fileStorageService.uploadFile(coverImage, coverImageKey);
                coverImageUrl = fileStorageService.generatePermanentUrl(coverImageKey);
                log.info("   ✅ Uploaded cover image: {}", coverImageUrl);
            }

            // Step 2: Generate Pinecone Vector ID first
            String tempVectorId = "guide_" + UUID.randomUUID().toString().substring(0, 8);
            
            // Step 3: Create and save UserGuide entity (with pineconeVectorId)
            UserGuide userGuide = UserGuide.builder()
                    .title(request.getTitle())
                    .shortDescription(request.getShortDescription())
                    .category(request.getCategory())
                    .difficulty(request.getDifficulty())
                    .contentText(request.getContentText())
                    .prerequisites(request.getPrerequisites())
                    .tags(request.getTags() != null ? request.getTags().toArray(new String[0]) : new String[0])
                    .keywords(request.getKeywords() != null ? request.getKeywords().toArray(new String[0]) : new String[0])
                    .relatedGuideIds(request.getRelatedGuideIds() != null ? request.getRelatedGuideIds().toArray(new Long[0]) : new Long[0])
                    .searchableQueries(request.getSearchableQueries() != null ? request.getSearchableQueries().toArray(new String[0]) : null)
                    .coverImageUrl(coverImageUrl)
                    .pineconeVectorId(tempVectorId)
                    .pineconeNamespace(pineconeNamespaceConfig.getUserGuidesNamespace())
                    .author(request.getAuthor())
                    .version(request.getVersion() != null ? request.getVersion() : "1.0")
                    .isActive(true)
                    .viewCount(0)
                    .helpfulCount(0)
                    .build();

            userGuide = userGuideRepository.save(userGuide);
            log.info("   ✅ Saved UserGuide entity with ID: {} and vectorId: {}", userGuide.getId(), tempVectorId);

            // Step 3: Create and save GuideSteps
            List<GuideStep> steps = new ArrayList<>();
            for (int i = 0; i < request.getSteps().size(); i++) {
                var stepDTO = request.getSteps().get(i);

                // Upload step screenshot if provided
                String screenshotUrl = null;
                if (stepImages != null && i < stepImages.size() && !stepImages.get(i).isEmpty()) {
                    String stepImageKey = String.format("guides/%d/steps/%d_%s",
                            userGuide.getId(),
                            stepDTO.getStepOrder(),
                            stepImages.get(i).getOriginalFilename());
                    fileStorageService.uploadFile(stepImages.get(i), stepImageKey);
                    screenshotUrl = fileStorageService.generatePermanentUrl(stepImageKey);
                }

                GuideStep step = GuideStep.builder()
                        .userGuide(userGuide)
                        .stepOrder(stepDTO.getStepOrder())
                        .title(stepDTO.getTitle())
                        .description(stepDTO.getDescription())
                        .screenLocation(stepDTO.getScreenLocation())
                        .uiElement(stepDTO.getUiElement())
                        .expectedResult(stepDTO.getExpectedResult())
                        .screenshotUrl(screenshotUrl)
                        .videoUrl(stepDTO.getVideoUrl())
                        .tips(stepDTO.getTips())
                        .commonMistakes(stepDTO.getCommonMistakes())
                        .build();

                steps.add(step);
            }

            steps = guideStepRepository.saveAll(steps);
            userGuide.setSteps(steps);
            log.info("   ✅ Saved {} guide steps", steps.size());

            // Step 4: Generate embedding and index to Pinecone (using the pre-generated vectorId)
            indexToPinecone(userGuide);

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("✅ Successfully indexed guide '{}' in {}ms", request.getTitle(), processingTime);

            return IndexingResultResponse.builder()
                    .success(true)
                    .message("Guide indexed successfully")
                    .guideId(userGuide.getId())
                    .pineconeVectorId(userGuide.getPineconeVectorId())
                    .coverImageUrl(coverImageUrl)
                    .totalSteps(steps.size())
                    .processingTimeMs(processingTime)
                    .build();

        } catch (Exception e) {
            log.error("❌ Failed to index guide: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.GUIDE_INDEXING_FAILED);
        }
    }

    /**
     * Index guide to Pinecone vector store with optimized content
     */
    private String indexToPinecone(UserGuide userGuide) {
        try {
            log.info("   🔍 Generating embedding for guide ID: {}", userGuide.getId());

            // Build optimized content for embedding (less noise, better semantic quality)
            String contentForEmbedding = buildOptimizedContentForEmbedding(userGuide);

            // Use the pre-generated vector ID from entity
            String vectorId = userGuide.getPineconeVectorId();

            // Create Document with metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("id", userGuide.getId());
            metadata.put("title", userGuide.getTitle());
            metadata.put("category", userGuide.getCategory().name());
            metadata.put("difficulty", userGuide.getDifficulty().name());
            metadata.put("namespace", pineconeNamespaceConfig.getUserGuidesNamespace());
            metadata.put("isActive", userGuide.getIsActive());

            if (userGuide.getTags() != null && userGuide.getTags().length > 0) {
                metadata.put("tags", String.join(",", userGuide.getTags()));
            }
            if (userGuide.getKeywords() != null && userGuide.getKeywords().length > 0) {
                metadata.put("keywords", String.join(",", userGuide.getKeywords()));
            }

            Document document = new Document(vectorId, contentForEmbedding, metadata);

            // Add to vector store
            vectorStore.add(List.of(document));

            log.info("   ✅ Indexed to Pinecone with vector ID: {}", vectorId);
            return vectorId;

        } catch (Exception e) {
            log.error("❌ Failed to index to Pinecone: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.GUIDE_INDEXING_FAILED);
        }
    }
    
    /**
     * Build optimized content for embedding - Clean, semantic-rich
     * Less noise, better structure for vector search
     */
    private String buildOptimizedContentForEmbedding(UserGuide userGuide) {
        StringBuilder content = new StringBuilder();
        
        // 1. Core content (clean, no "Title:" prefix)
        content.append(userGuide.getTitle()).append(". ");
        content.append(userGuide.getShortDescription()).append("\n\n");
        
        // 2. Main content
        if (userGuide.getContentText() != null && !userGuide.getContentText().isBlank()) {
            content.append(userGuide.getContentText()).append("\n\n");
        }
        
        // 2.5. CRITICAL: Searchable queries for natural language matching
        if (userGuide.getSearchableQueries() != null && userGuide.getSearchableQueries().length > 0) {
            content.append("Câu hỏi thường gặp:\n");
            for (String query : userGuide.getSearchableQueries()) {
                content.append("- ").append(query).append("\n");
            }
            content.append("\n");
        }
        
        // 3. Steps (enriched with context)
        if (userGuide.getSteps() != null && !userGuide.getSteps().isEmpty()) {
            content.append("Các bước thực hiện:\n");
            for (GuideStep step : userGuide.getSteps()) {
                content.append(String.format("%d. %s: %s\n",
                    step.getStepOrder(),
                    step.getTitle(),
                    step.getDescription()));
                
                // Add UI context for better matching
                if (step.getScreenLocation() != null && !step.getScreenLocation().isBlank()) {
                    content.append("   Màn hình: ").append(step.getScreenLocation()).append("\n");
                }
                
                if (step.getTips() != null && !step.getTips().isBlank()) {
                    content.append("   💡 ").append(step.getTips()).append("\n");
                }
            }
        }
        
        // 4. Keywords for better matching (at the end)
        if (userGuide.getKeywords() != null && userGuide.getKeywords().length > 0) {
            content.append("\n");
            content.append(String.join(", ", userGuide.getKeywords()));
        }
        
        return content.toString();
    }

    @Override
    @Transactional(transactionManager = "userGuideTransactionManager", readOnly = true)
    public UserGuideSearchResponse searchGuides(UserGuideSearchRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("🔍 Searching guides with query: '{}'", request.getQuery());

        try {
            // Build search request for Pinecone
            SearchRequest searchRequest = SearchRequest.query(request.getQuery())
                    .withTopK(request.getTopK())
                    .withSimilarityThreshold(request.getMinScore());

            // Perform vector search
            List<org.springframework.ai.document.Document> similarDocs = vectorStore.similaritySearch(searchRequest);

            // Create score map based on result order (first result = highest score)
            Map<Long, Double> scoreMap = new HashMap<>();
            for (int i = 0; i < similarDocs.size(); i++) {
                org.springframework.ai.document.Document doc = similarDocs.get(i);
                if (doc.getMetadata() != null && doc.getMetadata().containsKey("id")) {
                    try {
                        Object idObj = doc.getMetadata().get("id");
                        Long docId;
                        if (idObj instanceof Number) {
                            docId = ((Number) idObj).longValue();
                        } else if (idObj instanceof String) {
                            docId = Long.parseLong((String) idObj);
                        } else {
                            continue;
                        }
                        
                        // Calculate score: First result gets highest score
                        // Score decreases linearly: 1.0, 0.95, 0.90, 0.85...
                        double score = 1.0 - (i * 0.05);
                        score = Math.max(score, request.getMinScore()); // Ensure above threshold
                        
                        scoreMap.put(docId, score);
                        log.debug("   📊 Vector result {}: Guide ID {} - Estimated score: {}", i+1, docId, score);
                    } catch (NumberFormatException e) {
                        log.warn("   ⚠️ Invalid id format in result {}", i);
                    }
                }
            }

            // Extract guide IDs from results with error handling
            List<Long> guideIds = similarDocs.stream()
                    .filter(doc -> doc.getMetadata() != null && doc.getMetadata().containsKey("id"))
                    .map(doc -> {
                        try {
                            Object idObj = doc.getMetadata().get("id");
                            if (idObj instanceof Number) {
                                return ((Number) idObj).longValue();
                            } else if (idObj instanceof String) {
                                return Long.parseLong((String) idObj);
                            } else {
                                log.warn("⚠️ Invalid metadata id type: {}", idObj.getClass());
                                return null;
                            }
                        } catch (NumberFormatException e) {
                            log.warn("⚠️ Failed to parse guide ID: {}", doc.getMetadata().get("id"));
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
            
            if (guideIds.isEmpty() && !similarDocs.isEmpty()) {
                log.warn("⚠️ Found {} similar docs but couldn't extract any valid guide IDs", similarDocs.size());
            }

            // Fetch full guides from PostgreSQL
            List<UserGuide> guides = userGuideRepository.findAllById(guideIds);

            // Apply filters
            if (request.getCategory() != null) {
                guides = guides.stream()
                        .filter(g -> g.getCategory() == request.getCategory())
                        .collect(Collectors.toList());
            }
            if (request.getDifficulty() != null) {
                guides = guides.stream()
                        .filter(g -> g.getDifficulty() == request.getDifficulty())
                        .collect(Collectors.toList());
            }
            if (!request.getIncludeInactive()) {
                guides = guides.stream()
                        .filter(UserGuide::getIsActive)
                        .collect(Collectors.toList());
            }


            // Convert to responses with scores
            List<UserGuideResponse> responses = guides.stream()
                    .map(guide -> {
                        // Get score from pre-calculated scoreMap
                        Double score = scoreMap.getOrDefault(guide.getId(), 0.0);
                        log.debug("   📊 Guide ID {} mapped to score: {}", guide.getId(), score);
                        return userGuideMapper.toResponseWithScore(guide, score);
                    })
                    .collect(Collectors.toList());
            
            log.info("   ✅ Mapped {} guides with scores", responses.size());

            long processingTime = System.currentTimeMillis() - startTime;

            return UserGuideSearchResponse.builder()
                    .query(request.getQuery())
                    .totalResults(responses.size())
                    .processingTimeMs(processingTime)
                    .guides(responses)
                    .searchStrategy("vector-search")
                    .minScore(request.getMinScore())
                    .topK(request.getTopK())
                    .build();

        } catch (Exception e) {
            log.error("❌ Search failed: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    @Override
    @Transactional(transactionManager = "userGuideTransactionManager", readOnly = true)
    public UserGuideResponse getGuideById(Long guideId) {
        UserGuide guide = userGuideRepository.findById(guideId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_GUIDE_NOT_FOUND));
        return userGuideMapper.toResponse(guide);
    }

    @Override
    @Transactional(transactionManager = "userGuideTransactionManager", readOnly = true)
    public UserGuideResponse getGuideByVectorId(String vectorId) {
        UserGuide guide = userGuideRepository.findByPineconeVectorId(vectorId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_GUIDE_NOT_FOUND));
        return userGuideMapper.toResponse(guide);
    }

    @Override
    @Transactional(transactionManager = "userGuideTransactionManager", readOnly = true)
    public List<com.fpt.producerworkbench.dto.response.UserGuideSummaryResponse> getAllGuides(String category, String difficulty, Boolean isActive) {
        log.info("Fetching guides - category: {}, difficulty: {}, isActive: {}", category, difficulty, isActive);
        
        List<UserGuide> guides = userGuideRepository.findAll();
        
        // Apply filters
        if (isActive != null) {
            guides = guides.stream()
                    .filter(g -> g.getIsActive().equals(isActive))
                    .collect(Collectors.toList());
        }
        
        if (category != null && !category.isEmpty()) {
            GuideCategory guideCategory = GuideCategory.valueOf(category.toUpperCase().replace("-", "_"));
            guides = guides.stream()
                    .filter(g -> g.getCategory() == guideCategory)
                    .collect(Collectors.toList());
        }
        
        if (difficulty != null && !difficulty.isEmpty()) {
            com.fpt.producerworkbench.entity.userguide.GuideDifficulty guideDifficulty = 
                    com.fpt.producerworkbench.entity.userguide.GuideDifficulty.valueOf(difficulty.toUpperCase());
            guides = guides.stream()
                    .filter(g -> g.getDifficulty() == guideDifficulty)
                    .collect(Collectors.toList());
        }
        
        // Sort by createdAt desc (newest first)
        guides.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        
        return userGuideMapper.toSummaryResponseList(guides);
    }

    @Override
    @Transactional(transactionManager = "userGuideTransactionManager", readOnly = true)
    public List<UserGuideResponse> getAllActiveGuides() {
        List<UserGuide> guides = userGuideRepository.findByIsActiveTrue();
        return userGuideMapper.toResponseList(guides);
    }

    @Override
    @Transactional(transactionManager = "userGuideTransactionManager", readOnly = true)
    public List<UserGuideResponse> getGuidesByCategory(String category) {
        GuideCategory guideCategory = GuideCategory.valueOf(category.toUpperCase().replace("-", "_"));
        List<UserGuide> guides = userGuideRepository.findByCategoryAndIsActiveTrue(guideCategory);
        return userGuideMapper.toResponseList(guides);
    }

    @Override
    public List<String> getAllCategories() {
        return Arrays.stream(GuideCategory.values())
                .map(Enum::name)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(transactionManager = "userGuideTransactionManager")
    public void incrementViewCount(Long guideId) {
        userGuideRepository.incrementViewCount(guideId);
    }

    @Override
    @Transactional(transactionManager = "userGuideTransactionManager")
    public void incrementHelpfulCount(Long guideId) {
        userGuideRepository.incrementHelpfulCount(guideId);
    }

    @Override
    @Transactional(transactionManager = "userGuideTransactionManager")
    public void deleteGuide(Long guideId) {
        UserGuide guide = userGuideRepository.findById(guideId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_GUIDE_NOT_FOUND));

        // Step 1: Soft delete in database
        guide.setIsActive(false);
        userGuideRepository.save(guide);
        
        // Step 2: ✅ DELETE FROM PINECONE (CRITICAL FIX!)
        // Deleted guides MUST be removed from vector DB to prevent appearing in search
        try {
            String vectorId = guide.getPineconeVectorId();
            if (vectorId != null && !vectorId.isEmpty()) {
                vectorStore.delete(List.of(vectorId));
                log.info("   ✅ Deleted vector from Pinecone: {}", vectorId);
            } else {
                log.warn("   ⚠️ No Pinecone vector ID found for guide {}", guideId);
            }
        } catch (Exception e) {
            log.error("   ❌ Failed to delete from Pinecone: {}", e.getMessage());
            // Don't fail the transaction - guide is still soft deleted in DB
            // Vector can be cleaned up manually or via background job later
        }

        log.info("✅ Soft deleted guide ID: {} (DB + Pinecone)", guideId);
    }

    @Override
    @Transactional(transactionManager = "userGuideTransactionManager")
    public void permanentlyDeleteGuide(Long guideId) {
        log.info("🗑️ Permanently deleting guide ID: {}", guideId);
        
        // Step 1: Get guide first (need vectorId and URLs before deletion)
        UserGuide guide = userGuideRepository.findById(guideId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_GUIDE_NOT_FOUND));
        
        // Step 2: ✅ DELETE FROM PINECONE
        try {
            String vectorId = guide.getPineconeVectorId();
            if (vectorId != null && !vectorId.isEmpty()) {
                vectorStore.delete(List.of(vectorId));
                log.info("   ✅ Deleted Pinecone vector: {}", vectorId);
            }
        } catch (Exception e) {
            log.warn("   ⚠️ Pinecone delete failed: {}", e.getMessage());
        }
        
        // Step 3: ✅ DELETE S3 FILES
        List<String> s3FilesToDelete = new ArrayList<>();
        
        // Add cover image
        if (guide.getCoverImageUrl() != null && !guide.getCoverImageUrl().isEmpty()) {
            s3FilesToDelete.add(guide.getCoverImageUrl());
        }
        
        // Add step screenshots
        List<GuideStep> steps = guideStepRepository.findByUserGuideIdOrderByStepOrderAsc(guide.getId());
        for (GuideStep step : steps) {
            if (step.getScreenshotUrl() != null && !step.getScreenshotUrl().isEmpty()) {
                s3FilesToDelete.add(step.getScreenshotUrl());
            }
        }
        
        // Delete all S3 files
        for (String fileUrl : s3FilesToDelete) {
            try {
                String key = extractS3KeyFromUrl(fileUrl);
                fileStorageService.deleteFile(key);
                log.info("   ✅ Deleted S3 file: {}", key);
            } catch (Exception e) {
                log.warn("   ⚠️ S3 delete failed for {}: {}", fileUrl, e.getMessage());
            }
        }
        
        // Step 4: DELETE FROM DATABASE (CASCADE will delete steps automatically)
        userGuideRepository.deleteById(guideId);
        
        log.info("✅ Permanently deleted guide ID: {} (DB + Pinecone + {} S3 files)", 
                guideId, s3FilesToDelete.size());
    }

    @Override
    @Transactional(transactionManager = "userGuideTransactionManager")
    public UserGuideResponse updateGuide(Long guideId, UserGuideUpdateRequest request, MultipartFile coverImage, List<MultipartFile> stepImages) {
        log.info("📝 Updating guide ID: {}", guideId);
        
        try {
            // Step 1: Find existing guide
            UserGuide existingGuide = userGuideRepository.findById(guideId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_GUIDE_NOT_FOUND));
            
            String oldCoverImageUrl = existingGuide.getCoverImageUrl();
            
            // Step 2: Update basic fields (only if provided)
            if (request.getTitle() != null) {
                existingGuide.setTitle(request.getTitle());
            }
            if (request.getShortDescription() != null) {
                existingGuide.setShortDescription(request.getShortDescription());
            }
            if (request.getCategory() != null) {
                existingGuide.setCategory(request.getCategory());
            }
            if (request.getDifficulty() != null) {
                existingGuide.setDifficulty(request.getDifficulty());
            }
            if (request.getContentText() != null) {
                existingGuide.setContentText(request.getContentText());
            }
            if (request.getPrerequisites() != null) {
                existingGuide.setPrerequisites(request.getPrerequisites());
            }
            if (request.getTags() != null) {
                existingGuide.setTags(request.getTags().toArray(new String[0]));
            }
            if (request.getKeywords() != null) {
                existingGuide.setKeywords(request.getKeywords().toArray(new String[0]));
            }
            if (request.getRelatedGuideIds() != null) {
                existingGuide.setRelatedGuideIds(request.getRelatedGuideIds().toArray(new Long[0]));
            }
            if (request.getSearchableQueries() != null) {
                existingGuide.setSearchableQueries(request.getSearchableQueries().toArray(new String[0]));
            }
            if (request.getIsActive() != null) {
                existingGuide.setIsActive(request.getIsActive());
            }
            if (request.getVersion() != null) {
                existingGuide.setVersion(request.getVersion());
            }
            
            // Step 3: Handle cover image update
            if (coverImage != null && !coverImage.isEmpty()) {
                // Delete old cover image from S3
                if (oldCoverImageUrl != null && !oldCoverImageUrl.isEmpty()) {
                    try {
                        String oldKey = extractS3KeyFromUrl(oldCoverImageUrl);
                        fileStorageService.deleteFile(oldKey);
                        log.info("   ✅ Deleted old cover image: {}", oldKey);
                    } catch (Exception e) {
                        log.warn("   ⚠️ Failed to delete old cover image: {}", e.getMessage());
                    }
                }
                
                // Upload new cover image
                String coverImageKey = String.format("guides/cover/%s_%s",
                        System.currentTimeMillis(),
                        coverImage.getOriginalFilename());
                fileStorageService.uploadFile(coverImage, coverImageKey);
                String newCoverImageUrl = fileStorageService.generatePermanentUrl(coverImageKey);
                existingGuide.setCoverImageUrl(newCoverImageUrl);
                log.info("   ✅ Uploaded new cover image: {}", newCoverImageUrl);
            }
            
            // Step 4: Handle steps update (if provided)
            if (request.getSteps() != null && !request.getSteps().isEmpty()) {
                // Fetch old steps để map screenshots cũ
                List<GuideStep> oldSteps = guideStepRepository.findByUserGuideIdOrderByStepOrderAsc(guideId);
                
                // Map old screenshots by step order
                Map<Integer, String> oldScreenshotMap = new HashMap<>();
                if (oldSteps != null && !oldSteps.isEmpty()) {
                    for (GuideStep oldStep : oldSteps) {
                        if (oldStep.getScreenshotUrl() != null && !oldStep.getScreenshotUrl().isEmpty()) {
                            oldScreenshotMap.put(oldStep.getStepOrder(), oldStep.getScreenshotUrl());
                        }
                    }
                }
                
                // Delete ALL old steps bằng bulk DELETE query
                guideStepRepository.deleteByUserGuideId(guideId);
                
                // Flush để commit DELETE ngay lập tức
                entityManager.flush();
                
                log.info("   ✅ Deleted all old steps for guide {}", guideId);
                
                // Create new steps
                List<GuideStep> newSteps = new ArrayList<>();
                List<String> screenshotsToDelete = new ArrayList<>();
                
                for (int i = 0; i < request.getSteps().size(); i++) {
                    var stepDTO = request.getSteps().get(i);
                    
                    String screenshotUrl = null;
                    
                    // Check if có ảnh mới upload
                    if (stepImages != null && i < stepImages.size() && !stepImages.get(i).isEmpty()) {
                        // Upload ảnh mới
                        String stepImageKey = String.format("guides/%d/steps/%d_%s",
                                guideId,
                                stepDTO.getStepOrder(),
                                stepImages.get(i).getOriginalFilename());
                        fileStorageService.uploadFile(stepImages.get(i), stepImageKey);
                        screenshotUrl = fileStorageService.generatePermanentUrl(stepImageKey);
                        log.info("   ✅ Uploaded new step screenshot: {}", screenshotUrl);
                        
                        // Đánh dấu ảnh cũ để xóa (nếu có)
                        String oldScreenshot = oldScreenshotMap.get(stepDTO.getStepOrder());
                        if (oldScreenshot != null && !oldScreenshot.equals(screenshotUrl)) {
                            screenshotsToDelete.add(oldScreenshot);
                        }
                    } else {
                        // Không có ảnh mới → giữ lại ảnh cũ (nếu có)
                        screenshotUrl = oldScreenshotMap.get(stepDTO.getStepOrder());
                        if (screenshotUrl != null) {
                            log.info("   ♻️ Keeping existing screenshot: {}", screenshotUrl);
                        }
                    }
                    
                    GuideStep step = GuideStep.builder()
                            .userGuide(existingGuide)
                            .stepOrder(stepDTO.getStepOrder())
                            .title(stepDTO.getTitle())
                            .description(stepDTO.getDescription())
                            .screenLocation(stepDTO.getScreenLocation())
                            .uiElement(stepDTO.getUiElement())
                            .expectedResult(stepDTO.getExpectedResult())
                            .screenshotUrl(screenshotUrl)
                            .videoUrl(stepDTO.getVideoUrl())
                            .tips(stepDTO.getTips())
                            .commonMistakes(stepDTO.getCommonMistakes())
                            .build();
                    
                    newSteps.add(step);
                }
                
                // Xóa các screenshot không dùng nữa
                for (String screenshotToDelete : screenshotsToDelete) {
                    try {
                        String oldKey = extractS3KeyFromUrl(screenshotToDelete);
                        fileStorageService.deleteFile(oldKey);
                        log.info("   ✅ Deleted replaced step screenshot: {}", oldKey);
                    } catch (Exception e) {
                        log.warn("   ⚠️ Failed to delete old step screenshot: {}", e.getMessage());
                    }
                }
                
                // Xóa screenshot của các step bị remove (step order không còn trong request)
                for (Map.Entry<Integer, String> entry : oldScreenshotMap.entrySet()) {
                    Integer oldStepOrder = entry.getKey();
                    boolean stepStillExists = request.getSteps().stream()
                            .anyMatch(s -> s.getStepOrder().equals(oldStepOrder));
                    
                    if (!stepStillExists) {
                        try {
                            String oldKey = extractS3KeyFromUrl(entry.getValue());
                            fileStorageService.deleteFile(oldKey);
                            log.info("   ✅ Deleted screenshot of removed step: {}", oldKey);
                        } catch (Exception e) {
                            log.warn("   ⚠️ Failed to delete removed step screenshot: {}", e.getMessage());
                        }
                    }
                }
                
                newSteps = guideStepRepository.saveAll(newSteps);
                log.info("   ✅ Created {} new steps", newSteps.size());
            }
            
            // Step 5: Save updated guide to PostgreSQL
            existingGuide = userGuideRepository.save(existingGuide);
            log.info("   ✅ Updated guide in PostgreSQL");
            
            // Step 6: ✅ CONDITIONAL RE-INDEXING (PERFORMANCE FIX!)
            // Only re-index if searchable content changed
            boolean needsReindex = false;
            
            if (request.getTitle() != null || 
                request.getShortDescription() != null ||
                request.getContentText() != null ||
                request.getKeywords() != null ||
                request.getSearchableQueries() != null || // ⭐ CRITICAL for search!
                request.getTags() != null ||
                request.getSteps() != null) { // Steps contain searchable content
                needsReindex = true;
            }
            
            if (needsReindex) {
                try {
                    log.info("   🔄 Re-indexing to Pinecone (searchable content changed)...");
                    
                    // Delete old vector from Pinecone
                    String vectorId = existingGuide.getPineconeVectorId();
                    if (vectorId != null && !vectorId.isEmpty()) {
                        vectorStore.delete(List.of(vectorId));
                        log.info("   ✅ Deleted old vector from Pinecone: {}", vectorId);
                    }
                    
                    // Index new content
                    indexToPinecone(existingGuide);
                    log.info("   ✅ Re-indexed to Pinecone with new content");
                } catch (Exception e) {
                    log.error("❌ Failed to re-index to Pinecone: {}", e.getMessage(), e);
                    throw new AppException(ErrorCode.GUIDE_INDEXING_FAILED);
                }
            } else {
                log.info("   ⏭️ Skipping Pinecone re-index (metadata-only update: author/version/etc.)");
            }
            
            log.info("✅ Successfully updated guide ID: {}", guideId);
            return userGuideMapper.toResponse(existingGuide);
            
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Failed to update guide: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }
    
    /**
     * Extract S3 object key from full URL
     * Example: https://producer-workbench-media.s3.ap-southeast-1.amazonaws.com/guides/cover/image.jpg
     * Returns: guides/cover/image.jpg
     */
    private String extractS3KeyFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_FILE_KEY);
        }
        
        try {
            // Remove domain part and get only the object key
            int lastSlashIndex = url.indexOf(".com/");
            if (lastSlashIndex == -1) {
                // If it's already just a key (no domain)
                return url;
            }
            return url.substring(lastSlashIndex + 5); // +5 to skip ".com/"
        } catch (Exception e) {
            log.error("Failed to extract S3 key from URL: {}", url, e);
            throw new AppException(ErrorCode.INVALID_FILE_KEY);
        }
    }

    @Override
    @Transactional(transactionManager = "userGuideTransactionManager", readOnly = true)
    public UserGuideStatsResponse getIndexStats() {
        // TODO: Implement stats
        throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
    }

    @Override
    @Transactional(transactionManager = "userGuideTransactionManager")
    public Map<String, Object> reindexAllGuides() {
        // TODO: Implement reindex
        throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
    }

    @Override
    @Transactional(transactionManager = "userGuideTransactionManager")
    public Map<String, Object> indexMultipleGuides(List<UserGuideIndexRequest> requests) {
        // TODO: Implement batch index
        throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
    }
}
