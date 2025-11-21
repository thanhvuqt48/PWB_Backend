package com.fpt.producerworkbench.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fpt.producerworkbench.configuration.GeminiProperties;
import com.fpt.producerworkbench.dto.request.ExplanationRequest;
import com.fpt.producerworkbench.dto.request.VectorSearchRequest;
import com.fpt.producerworkbench.dto.response.ExplanationResponse;
import com.fpt.producerworkbench.dto.response.VectorSearchResponse;
import com.fpt.producerworkbench.service.AiExplanationService;
import com.fpt.producerworkbench.service.VectorDbIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of AI explanation service using RAG
 * (Retrieval-Augmented Generation)
 * 
 * Flow:
 * 1. Nhận highlighted text từ user
 * 2. Vector search để tìm related terms trong Pinecone
 * 3. Build context từ search results
 * 4. Gọi Gemini để generate explanation tiếng Việt
 * 5. Return explanation + related terms
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiExplanationServiceImpl implements AiExplanationService {
    
    private final VectorDbIndexingService vectorDbIndexingService;
    private final GeminiProperties geminiProperties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    
    /**
     * Prompt template cho Gemini để generate explanation
     */
    private static final String EXPLANATION_PROMPT_TEMPLATE = """
            Bạn là chuyên gia về sản xuất âm nhạc và âm thanh. Nhiệm vụ của bạn là giải thích các thuật ngữ âm nhạc bằng tiếng Việt một cách dễ hiểu.
            
            Người dùng đang highlight thuật ngữ: "{highlightedText}"
            
            Dưới đây là các thuật ngữ liên quan từ cơ sở dữ liệu:
            {relatedTermsContext}
            
            Hãy tạo một explanation ngắn gọn (2-3 câu) bằng tiếng Việt để giải thích thuật ngữ này.
            
            Yêu cầu:
            - Giải thích dễ hiểu, phù hợp cho người mới bắt đầu
            - Nếu thuật ngữ có trong database, sử dụng định nghĩa đó làm base
            - Nếu không có trong database, dựa vào kiến thức của bạn
            - Đưa ra ví dụ thực tế nếu có thể
            - Chỉ trả về explanation, không thêm tiêu đề hay metadata
            
            Explanation:
            """;
    
    /**
     * Generate explanation cho highlighted text sử dụng RAG
     */
    public ExplanationResponse generateExplanation(ExplanationRequest request) {
        long startTime = System.currentTimeMillis();
        
        log.info("🔍 Generating explanation for: '{}'", request.getHighlightedText());
        
        // Step 1: Vector search để tìm related terms
        VectorSearchResponse searchResponse = vectorDbIndexingService.searchSimilarTerms(
                VectorSearchRequest.builder()
                        .query(request.getHighlightedText())
                        .topK(request.getMaxRelatedTerms())
                        .minScore(0.3) // Lower threshold for short queries (EDM, DAW, etc.)
                        .build()
        );
        
        boolean foundInDatabase = !searchResponse.getResults().isEmpty();
        log.info("   Found {} related terms in vector DB", searchResponse.getResults().size());
        
        // Step 2: Build context từ search results
        String relatedTermsContext = buildContextFromSearchResults(searchResponse);
        
        // Step 3: Generate explanation với Gemini
        String explanation = generateExplanationWithGemini(
                request.getHighlightedText(), 
                relatedTermsContext
        );
        
        // Step 4: Convert search results to RelatedTerm DTOs
        List<ExplanationResponse.RelatedTerm> relatedTerms = searchResponse.getResults().stream()
                .map(result -> ExplanationResponse.RelatedTerm.builder()
                        .term(result.getTerm())
                        .definition(result.getDefinition())
                        .category(result.getCategory())
                        .similarity(result.getSimilarityScore())
                        .synonyms(result.getSynonyms())
                        .build())
                .collect(Collectors.toList());
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        log.info("✅ Explanation generated in {}ms", processingTime);
        
        return ExplanationResponse.builder()
                .originalText(request.getHighlightedText())
                .explanation(explanation)
                .relatedTerms(relatedTerms)
                .foundInDatabase(foundInDatabase)
                .processingTimeMs(processingTime)
                .model(geminiProperties.getModel())
                .build();
    }
    
    /**
     * Build context string từ vector search results
     */
    private String buildContextFromSearchResults(VectorSearchResponse searchResponse) {
        if (searchResponse.getResults().isEmpty()) {
            return "Không tìm thấy thuật ngữ liên quan trong cơ sở dữ liệu.";
        }
        
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < searchResponse.getResults().size(); i++) {
            VectorSearchResponse.SimilarTerm term = searchResponse.getResults().get(i);
            context.append(String.format(
                    "%d. %s: %s (Category: %s)\n",
                    i + 1,
                    term.getTerm(),
                    term.getDefinition(),
                    term.getCategory()
            ));
            
            if (term.getExamples() != null && !term.getExamples().isEmpty()) {
                context.append("   Ví dụ: ").append(String.join(", ", term.getExamples())).append("\n");
            }
        }
        
        return context.toString();
    }
    
    /**
     * Gọi Gemini REST API để generate explanation với context
     */
    private String generateExplanationWithGemini(String highlightedText, String relatedTermsContext) {
        try {
            // Build prompt từ template
            String prompt = EXPLANATION_PROMPT_TEMPLATE
                    .replace("{highlightedText}", highlightedText)
                    .replace("{relatedTermsContext}", relatedTermsContext);
            
            // Build request body theo Gemini API format
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", List.of(
                    Map.of("parts", List.of(
                            Map.of("text", prompt)
                    ))
            ));
            
            String url = String.format("%s/models/%s:generateContent",
                    geminiProperties.getBaseUrl(),
                    geminiProperties.getModel());
            
            log.debug("📤 Sending prompt to Gemini REST API...");
            
            // Call Gemini API
            WebClient webClient = webClientBuilder.build();
            String response = webClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", geminiProperties.getApiKey())
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            // Parse response
            JsonNode jsonResponse = objectMapper.readTree(response);
            String generatedText = jsonResponse
                    .path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text").asText();
            
            log.debug("📥 Received response from Gemini");
            return generatedText.trim();
            
        } catch (Exception e) {
            log.error("❌ Error calling Gemini API", e);
            return "Xin lỗi, không thể tạo explanation lúc này. Vui lòng thử lại sau.";
        }
    }
}
