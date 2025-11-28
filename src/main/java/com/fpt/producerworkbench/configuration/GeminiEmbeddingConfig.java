package com.fpt.producerworkbench.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Custom Gemini Embedding Configuration
 * Provides 768-dimensional embeddings using text-embedding-004
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GeminiEmbeddingConfig {

    private final GeminiConfig geminiConfig;

    @Bean
    @Primary
    public GeminiEmbeddingModel geminiEmbeddingModel() {
        log.info("🔧 Creating Gemini EmbeddingModel");
        log.info("   Model: {}", geminiConfig.getEmbeddingModel());
        log.info("   Dimension: {}", geminiConfig.getEmbeddingDimension());
        
        return new GeminiEmbeddingModel(geminiConfig);
    }

    /**
     * Custom Gemini Embedding Model Implementation
     */
    @Slf4j
    public static class GeminiEmbeddingModel extends AbstractEmbeddingModel {
        
        private final GeminiConfig geminiConfig;
        private final WebClient webClient = WebClient.builder().build();

        public GeminiEmbeddingModel(GeminiConfig geminiConfig) {
            this.geminiConfig = geminiConfig;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            try {
                List<String> texts = request.getInstructions();
                
                log.debug("🔍 Generating embeddings for {} text(s)", texts.size());
                
                // Call Gemini API for each text
                List<Embedding> embeddings = texts.stream()
                        .map(this::generateEmbedding)
                        .collect(Collectors.toList());
                
                return new EmbeddingResponse(embeddings);
                
            } catch (Exception e) {
                log.error("❌ Failed to generate embeddings: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to generate embeddings", e);
            }
        }

        @Override
        public int dimensions() {
            return geminiConfig.getEmbeddingDimension(); // 768
        }

        @Override
        public float[] embed(org.springframework.ai.document.Document document) {
            Embedding embedding = generateEmbedding(document.getContent());
            return embedding.getOutput();
        }

        /**
         * Generate embedding for a single text using Gemini API
         */
        private Embedding generateEmbedding(String text) {
            try {
                String endpoint = geminiConfig.buildEmbeddingUrl();
                
                // Request body
                Map<String, Object> requestBody = Map.of(
                    "model", "models/" + geminiConfig.getEmbeddingModel(),
                    "content", Map.of(
                        "parts", List.of(Map.of("text", text))
                    )
                );
                
                // Call Gemini API with header auth (secure)
                @SuppressWarnings("unchecked")
                Map<String, Object> response = webClient.post()
                        .uri(endpoint)
                        .header("Content-Type", "application/json")
                        .header("X-goog-api-key", geminiConfig.getApiKey())
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();
                
                // Extract embedding values
                @SuppressWarnings("unchecked")
                Map<String, Object> embedding = (Map<String, Object>) response.get("embedding");
                
                @SuppressWarnings("unchecked")
                List<Double> values = (List<Double>) embedding.get("values");
                
                // Convert to float array
                float[] floatValues = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    floatValues[i] = values.get(i).floatValue();
                }
                
                log.debug("   ✅ Generated embedding with {} dimensions", floatValues.length);
                
                return new Embedding(floatValues, 0);
                
            } catch (Exception e) {
                log.error("❌ Failed to call Gemini API: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to call Gemini API", e);
            }
        }
    }
}
