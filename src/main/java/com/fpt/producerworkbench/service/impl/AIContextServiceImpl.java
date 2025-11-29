package com.fpt.producerworkbench.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.producerworkbench.configuration.GeminiConfig;
import com.fpt.producerworkbench.dto.request.AIContextRequest;
import com.fpt.producerworkbench.dto.request.UserGuideSearchRequest;
import com.fpt.producerworkbench.dto.response.AIContextualResponse;
import com.fpt.producerworkbench.dto.response.UserGuideResponse;
import com.fpt.producerworkbench.dto.response.UserGuideSearchResponse;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.service.AIContextService;
import com.fpt.producerworkbench.service.UserGuideIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIContextServiceImpl implements AIContextService {

    private final UserGuideIndexingService userGuideIndexingService;
    private final GeminiConfig geminiConfig;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final ChatClient aiChatClient;
    private final org.springframework.ai.chat.memory.ChatMemory redisChatMemory;



    /**
     * Prompt template for intent analysis
     */
    private static final String INTENT_ANALYSIS_PROMPT = """
            Analyze the following user query and classify its intent into ONE of these categories:
            - "how-to": User wants to learn how to do something
            - "troubleshoot": User encountered an error or problem
            - "find-feature": User is looking for a specific feature
            - "best-practice": User wants advice or best practices
            
            Query: "%s"
            
            Respond with ONLY the intent category (no explanation).
            Intent:""";

    /**
     * Prompt template for contextual guidance generation
     * AI will format answer with markdown and embed images
     */
    private static final String CONTEXTUAL_GUIDANCE_PROMPT = """
            Bạn là AI assistant cho Producer Workbench - một nền tảng sản xuất âm nhạc.
            
            User query: "%s"
            Intent detected: %s
            Current page: %s
            User role: %s
            
            Relevant guides found (với ảnh):
            %s
            
            NHIỆM VỤ CỦA BẠN:
            1. Trả lời câu hỏi của user bằng tiếng Việt, chi tiết và dễ hiểu
            2. SỬ DỤNG MARKDOWN để format câu trả lời:
               - Headers (##, ###) cho sections
               - **Bold** cho điểm quan trọng
               - Lists (-, 1., 2.) cho steps
               - Code blocks nếu cần
            
            3. NHÚNG HÌNH ẢNH trực tiếp vào câu trả lời:
               - Dùng syntax: ![Mô tả](URL)
               - Chọn ảnh phù hợp nhất với từng bước
               - Đặt ảnh ngay sau phần giải thích
               - Cover image của guide nếu relevant
               - Screenshots của các bước cụ thể
            
            4. CHỈ chọn guides và images THỰC SỰ liên quan đến câu hỏi
            5. Nếu không có guide phù hợp, dựa vào kiến thức chung nhưng nói rõ
            6. Format rõ ràng, dễ đọc, có cấu trúc
            
            VÍ DỤ FORMAT:
            ```
            ## Hướng Dẫn Login Vào Hệ Thống
            
            Để đăng nhập, bạn làm theo các bước sau:
            
            ### Bước 1: Chọn Phương Thức Đăng Nhập
            Trên trang login, bạn có thể chọn:
            - Đăng nhập bằng Google
            - Đăng nhập bằng Email + Password
            
            ![Giao diện đăng nhập](https://...screenshot1.jpg)
            
            ### Bước 2: Nhập Thông Tin
            ...
            
            ![Nhập thông tin](https://...screenshot2.jpg)
            ```
            
            Format response as JSON:
            {
              "answer": "## Câu trả lời với markdown và ![images](urls)...",
              "suggestedActions": ["Action 1", "Action 2", "Action 3"],
              "relatedTopics": ["Topic 1", "Topic 2", "Topic 3"],
              "confidence": 0.95
            }
            
            JSON Response:""";


    @Override
    public AIContextualResponse getContextualGuidance(AIContextRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("🤖 Generating contextual guidance for: '{}'", request.getQuery());

        try {
            // Step 1: Analyze intent
            String intent = analyzeIntent(request.getQuery());
            log.info("   Intent detected: {}", intent);

            // Step 2: Search guides with images (parallel to QuestionAnswerAdvisor RAG)
            UserGuideSearchResponse guidesWithImages = searchGuidesWithImages(request);
            log.info("📸 Found {} guides with images", 
                guidesWithImages != null ? guidesWithImages.getTotalResults() : 0);

            // Step 3: Build enhanced prompt with images
            String enhancedPrompt = buildPromptWithImages(request, intent, guidesWithImages);
            log.info("📝 Built prompt: {} chars", enhancedPrompt.length());

            // Step 4: Use ChatClient with Advisors
            // QuestionAnswerAdvisor will add RAG text context automatically
            // We manually added images in the prompt above
            log.info("🔄 Calling ChatClient with sessionId: {}", request.getSessionId());
            
            String aiResponse;
            try {
                aiResponse = aiChatClient.prompt()
                        .user(enhancedPrompt)
                        .advisors(advisorSpec -> advisorSpec
                                .param("conversationId", request.getSessionId())
                        )
                        .call()
                        .content();
                        
                log.info("✅ ChatClient response received: {} chars", aiResponse.length());
            } catch (Exception chatError) {
                log.error("❌ ChatClient call failed: {}", chatError.getMessage(), chatError);
                throw new RuntimeException("ChatClient error: " + chatError.getMessage(), chatError);
            }

            // Step 5: MANUALLY SAVE messages to Redis
            // MessageChatMemoryAdvisor only LOADS messages, doesn't auto-save!
            try {
                List<org.springframework.ai.chat.messages.Message> conversationMessages = List.of(
                    new org.springframework.ai.chat.messages.UserMessage(request.getQuery()),
                    new org.springframework.ai.chat.messages.AssistantMessage(aiResponse)
                );
                redisChatMemory.add(request.getSessionId(), conversationMessages);
                log.info("💾 Saved conversation to Redis (session: {})", request.getSessionId());
            } catch (Exception saveError) {
                log.warn("⚠️ Failed to save conversation to Redis: {}", saveError.getMessage());
                // Don't fail the request if save fails
            }

            // Step 6: Parse follow-up questions from AI response
            List<String> followUpQuestions = parseFollowUpQuestions(aiResponse);
            log.info("💡 Extracted {} follow-up questions", followUpQuestions.size());

            // Step 7: Build response with guides
            long processingTime = System.currentTimeMillis() - startTime;
            log.info("⏱️ Total processing time: {}ms", processingTime);

            return AIContextualResponse.builder()
                    .answer(cleanAnswerFromFollowUp(aiResponse)) // Remove FOLLOW_UP section
                    .intent(intent)
                    .confidence(0.85)
                    .relevantGuides(guidesWithImages != null ? guidesWithImages.getGuides() : null)
                    .suggestedActions(followUpQuestions.isEmpty() ? null : followUpQuestions)
                    .processingTimeMs(processingTime)
                    .model(geminiConfig.getModel())
                    .build();

        } catch (Exception e) {
            log.error("❌ Failed to generate contextual guidance: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }
    
    /**
     * Search guides with images from PostgreSQL
     */
    private UserGuideSearchResponse searchGuidesWithImages(AIContextRequest request) {
        try {
            int maxGuides = request.getMaxGuides() != null ? request.getMaxGuides() : 3;
            
            UserGuideSearchRequest searchRequest = UserGuideSearchRequest.builder()
                    .query(request.getQuery())
                    .topK(maxGuides)
                    .minScore(0.7)
                    .includeInactive(false)
                    .build();
            
            return userGuideIndexingService.searchGuides(searchRequest);
        } catch (Exception e) {
            log.warn("⚠️ Failed to search guides with images: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Build prompt with images from PostgreSQL guides
     * QuestionAnswerAdvisor will add RAG text context on top
     */
    private String buildPromptWithImages(
            AIContextRequest request, 
            String intent,
            UserGuideSearchResponse guidesWithImages) {
        
        StringBuilder prompt = new StringBuilder();
        
        // System role
        prompt.append("Bạn là AI assistant cho Producer Workbench.\n\n");
        
        // Context
        prompt.append("Context:\n");
        if (request.getCurrentPage() != null) {
            prompt.append("- Current page: ").append(request.getCurrentPage()).append("\n");
        }
        if (request.getUserRole() != null) {
            prompt.append("- User role: ").append(request.getUserRole()).append("\n");
        }
        prompt.append("- Intent: ").append(intent).append("\n\n");
        
        // ✅ AVAILABLE IMAGES (from PostgreSQL)
        if (guidesWithImages != null && guidesWithImages.getGuides() != null && !guidesWithImages.getGuides().isEmpty()) {
            prompt.append("---\n");
            prompt.append("AVAILABLE IMAGES (sử dụng để minh họa):\n\n");
            
            for (UserGuideResponse guide : guidesWithImages.getGuides()) {
                prompt.append("📚 Guide: \"").append(guide.getTitle()).append("\"\n");
                
                // Cover image
                if (guide.getCoverImageUrl() != null && !guide.getCoverImageUrl().isEmpty()) {
                    prompt.append("   Cover: ![")
                          .append(guide.getTitle())
                          .append("](")
                          .append(guide.getCoverImageUrl())
                          .append(")\n");
                }
                
                // Step screenshots
                if (guide.getSteps() != null && !guide.getSteps().isEmpty()) {
                    for (var step : guide.getSteps()) {
                        if (step.getScreenshotUrl() != null && !step.getScreenshotUrl().isEmpty()) {
                            prompt.append("   Bước ")
                                  .append(step.getStepOrder())
                                  .append(": ![")
                                  .append(step.getTitle())
                                  .append("](")
                                  .append(step.getScreenshotUrl())
                                  .append(")\n");
                        }
                    }
                }
                
                prompt.append("\n");
            }
            
            prompt.append("---\n\n");
        }
        
        // Instructions
        prompt.append("Hướng dẫn:\n");
        prompt.append("- Trả lời bằng tiếng Việt, chi tiết và dễ hiểu\n");
        prompt.append("- Sử dụng markdown format (headers, bold, lists)\n");
        prompt.append("- **QUAN TRỌNG**: Nhúng ảnh từ danh sách AVAILABLE IMAGES ở trên vào đúng chỗ trong câu trả lời\n");
        prompt.append("- Sử dụng markdown syntax: ![mô tả](url) để hiển thị ảnh\n");
        prompt.append("- Cover image nên đặt ở đầu hướng dẫn, screenshots đặt ở từng bước tương ứng\n");
        prompt.append("- **CUỐI CÙN**: Sau câu trả lời, gợi ý 3-5 câu hỏi tiếp theo bằng format:\n");
        prompt.append("  ---FOLLOW_UP---\n");
        prompt.append("  - Câu hỏi 1?\n");
        prompt.append("  - Câu hỏi 2?\n");
        prompt.append("  - Câu hỏi 3?\n\n");
        
        // User query
        prompt.append("Câu hỏi: ").append(request.getQuery());
        
        return prompt.toString();
    }
    
    /**
     * Parse follow-up questions from AI response
     * Format: ---FOLLOW_UP---\n- Question 1?\n- Question 2?\n
     */
    private List<String> parseFollowUpQuestions(String aiResponse) {
        List<String> questions = new ArrayList<>();
        
        if (aiResponse == null || !aiResponse.contains("---FOLLOW_UP---")) {
            return questions;
        }
        
        try {
            String[] parts = aiResponse.split("---FOLLOW_UP---");
            if (parts.length > 1) {
                String followUpSection = parts[1].trim();
                String[] lines = followUpSection.split("\n");
                
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
                        String question = trimmed.substring(1).trim();
                        if (!question.isEmpty()) {
                            questions.add(question);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse follow-up questions: {}", e.getMessage());
        }
        
        log.debug("Parsed {} follow-up questions", questions.size());
        return questions;
    }
    
    /**
     * Clean answer by removing FOLLOW_UP section
     */
    private String cleanAnswerFromFollowUp(String aiResponse) {
        if (aiResponse == null || !aiResponse.contains("---FOLLOW_UP---")) {
            return aiResponse;
        }
        
        return aiResponse.split("---FOLLOW_UP---")[0].trim();
    }

    @Override
    public String analyzeIntent(String query) {
        try {
            String prompt = String.format(INTENT_ANALYSIS_PROMPT, query);
            String response = callGeminiAPI(prompt);

            // Clean response - remove whitespace, quotes, and convert to lowercase
            String intent = response.trim()
                    .replace("\"", "")
                    .replace("'", "")
                    .toLowerCase();
            
            // Extract just the intent keyword (in case Gemini adds extra text)
            List<String> validIntents = List.of("how-to", "troubleshoot", "find-feature", "best-practice");
            for (String validIntent : validIntents) {
                if (intent.contains(validIntent)) {
                    log.info("   ✅ Detected intent: {}", validIntent);
                    return validIntent;
                }
            }

            log.warn("⚠️ Unknown intent '{}' from response '{}', defaulting to 'how-to'", intent, response);
            return "how-to";

        } catch (Exception e) {
            log.error("❌ Intent analysis failed: {}", e.getMessage(), e);
            return "how-to"; // Default fallback
        }
    }

    @Override
    public AIContextualResponse getQuickHelp(String query) {
        // Quick help - simpler version without full context
        return getContextualGuidance(
                AIContextRequest.builder()
                        .query(query)
                        .maxGuides(2)
                        .includeRelatedGuides(false)
                        .build()
        );
    }

    /**
     * Build context string from search results with image URLs
     */
    private String buildGuidesContext(UserGuideSearchResponse searchResponse) {
        if (searchResponse.getGuides().isEmpty()) {
            return "No relevant guides found in database.";
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < searchResponse.getGuides().size(); i++) {
            var guide = searchResponse.getGuides().get(i);
            context.append(String.format("\n--- Guide %d ---\n", i + 1));
            context.append(String.format("Title: %s\n", guide.getTitle()));
            context.append(String.format("Category: %s\n", guide.getCategory()));
            context.append(String.format("Description: %s\n", guide.getShortDescription()));
            context.append(String.format("Relevance Score: %.2f\n", guide.getRelevanceScore()));
            
            // Include cover image URL
            if (guide.getCoverImageUrl() != null && !guide.getCoverImageUrl().isBlank()) {
                context.append(String.format("Cover Image: %s\n", guide.getCoverImageUrl()));
            }

            if (guide.getSteps() != null && !guide.getSteps().isEmpty()) {
                context.append("Steps:\n");
                guide.getSteps().forEach(step -> {
                    context.append(String.format("  %d. %s\n", step.getStepOrder(), step.getTitle()));
                    context.append(String.format("     Description: %s\n", step.getDescription()));
                    
                    // Include step screenshot URL
                    if (step.getScreenshotUrl() != null && !step.getScreenshotUrl().isBlank()) {
                        context.append(String.format("     Screenshot: %s\n", step.getScreenshotUrl()));
                    }
                    
                    if (step.getExpectedResult() != null && !step.getExpectedResult().isBlank()) {
                        context.append(String.format("     Expected: %s\n", step.getExpectedResult()));
                    }
                });
            }
        }

        return context.toString();
    }

    /**
     * Call Gemini API for text generation
     */
    private String callGeminiAPI(String prompt) {
        try {
            // Log request details
            log.debug("🔍 Gemini API Request:");
            log.debug("   Prompt length: {} chars", prompt.length());
            log.debug("   First 200 chars: {}", prompt.substring(0, Math.min(200, prompt.length())));
            
            WebClient webClient = webClientBuilder.build();

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", prompt)
                            ))
                    )
            );

            // Build dynamic URL from config
            String apiUrl = String.format("%s/models/%s:generateContent",
                    geminiConfig.getBaseUrl(),
                    geminiConfig.getModel());
            
            log.debug("   API URL: {}", apiUrl);
            
            String response = webClient.post()
                    .uri(apiUrl)
                    .header("Content-Type", "application/json")
                    .header("X-goog-api-key", geminiConfig.getApiKey())
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Log response details
            log.debug("✅ Gemini API Response:");
            log.debug("   Response length: {} chars", response.length());
            log.debug("   First 500 chars: {}", response.substring(0, Math.min(500, response.length())));
            
            JsonNode responseJson = objectMapper.readTree(response);
            
            // Check for API errors
            if (responseJson.has("error")) {
                JsonNode error = responseJson.get("error");
                String errorMessage = error.path("message").asText("Unknown error");
                int errorCode = error.path("code").asInt(0);
                log.error("❌ Gemini API error ({}): {}", errorCode, errorMessage);
                throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
            }
            
            // Check if candidates exist
            if (!responseJson.has("candidates") || responseJson.get("candidates").isEmpty()) {
                log.error("❌ No candidates in Gemini response. Response: {}", response);
                throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
            }
            
            JsonNode firstCandidate = responseJson.path("candidates").get(0);
            
            // Check for blocked content
            if (firstCandidate.has("finishReason")) {
                String finishReason = firstCandidate.get("finishReason").asText();
                if (!"STOP".equals(finishReason)) {
                    log.warn("⚠️ Content blocked or incomplete. Finish reason: {}", finishReason);
                    if (firstCandidate.has("safetyRatings")) {
                        log.warn("   Safety ratings: {}", firstCandidate.get("safetyRatings"));
                    }
                }
            }
            
            // Extract text content
            JsonNode content = firstCandidate.path("content");
            JsonNode parts = content.path("parts");
            
            if (parts.isEmpty()) {
                log.error("❌ No content parts in Gemini response");
                throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
            }
            
            String text = parts.get(0).path("text").asText();
            
            if (text == null || text.isBlank()) {
                log.error("❌ Empty text in Gemini response");
                throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
            }
            
            return text;

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Gemini API call failed: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }
    
    /**
     * Extract JSON from markdown code blocks
     * Gemini often wraps JSON in ```json ... ``` or ``` ... ```
     */
    private String extractJsonFromMarkdown(String response) {
        if (response == null || response.isBlank()) {
            return response;
        }
        
        String trimmed = response.trim();
        
        // Pattern 1: ```json ... ```
        if (trimmed.startsWith("```json")) {
            int start = trimmed.indexOf('\n', 7); // After ```json
            int end = trimmed.lastIndexOf("```");
            if (start != -1 && end != -1 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        
        // Pattern 2: ``` ... ```
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n', 3); // After ```
            int end = trimmed.lastIndexOf("```");
            if (start != -1 && end != -1 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        
        // Pattern 3: Has backticks in middle
        if (trimmed.contains("```")) {
            // Try to extract {...} or [...]
            int jsonStart = trimmed.indexOf('{');
            int jsonEnd = trimmed.lastIndexOf('}');
            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                return trimmed.substring(jsonStart, jsonEnd + 1).trim();
            }
        }
        
        // No markdown, return as is
        return trimmed;
    }
    
    /**
     * AI Re-ranking: Let AI select the best guides from top candidates
     * This improves accuracy by letting AI choose the most relevant chunk
     */
    private List<UserGuideResponse> selectBestGuides(
            List<UserGuideResponse> candidates,
            String userQuery,
            String intent,
            int maxSelect) {
        
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        
        // If we have fewer than maxSelect, return all
        if (candidates.size() <= maxSelect) {
            return candidates;
        }
        
        try {
            log.info("🤖 AI Re-ranking: Selecting best {} from {} candidates", maxSelect, candidates.size());
            
            // Build re-ranking prompt
            StringBuilder prompt = new StringBuilder();
            prompt.append("Bạn là AI expert về hệ thống Producer Workbench.\n\n");
            prompt.append(String.format("USER QUERY: \"%s\"\n", userQuery));
            prompt.append(String.format("INTENT: %s\n\n", intent));
            prompt.append(String.format("Có %d guides sau. Hãy chọn %d guides PHÙ HỢP NHẤT với câu hỏi:\n\n", 
                candidates.size(), maxSelect));
            
            // Add candidates
            for (int i = 0; i < candidates.size(); i++) {
                var guide = candidates.get(i);
                prompt.append(String.format("Guide %d:\n", i + 1));
                prompt.append(String.format("  Title: %s\n", guide.getTitle()));
                prompt.append(String.format("  Category: %s\n", guide.getCategory()));
                prompt.append(String.format("  Description: %s\n", guide.getShortDescription()));
                prompt.append(String.format("  Score: %.3f\n\n", guide.getRelevanceScore()));
            }
            
            prompt.append("Trả về JSON với array chỉ số (1-indexed) của guides phù hợp nhất, sắp xếp theo độ relevance:\n");
            prompt.append("{ \"selected\": [1, 3] }");
            
            // Call AI
            String aiResponse = callGeminiAPI(prompt.toString());
            String jsonResponse = extractJsonFromMarkdown(aiResponse);
            JsonNode responseJson = objectMapper.readTree(jsonResponse);
            
            // Extract selected indices
            List<Integer> selectedIndices = new ArrayList<>();
            if (responseJson.has("selected")) {
                responseJson.get("selected").forEach(node -> 
                    selectedIndices.add(node.asInt())
                );
            }
            
            // Map indices back to guides
            List<UserGuideResponse> selected = new ArrayList<>();
            for (Integer index : selectedIndices) {
                if (index > 0 && index <= candidates.size()) {
                    selected.add(candidates.get(index - 1));  // 1-indexed to 0-indexed
                }
            }
            
            log.info("✅ AI selected {} guides: {}", selected.size(), selectedIndices);
            return selected.isEmpty() ? candidates.subList(0, Math.min(maxSelect, candidates.size())) : selected;
            
        } catch (Exception e) {
            log.warn("⚠️ AI re-ranking failed, using top {} by score: {}", maxSelect, e.getMessage());
            // Fallback: return top N by score
            return candidates.subList(0, Math.min(maxSelect, candidates.size()));
        }
    }
}
