package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.ExplanationRequest;
import com.fpt.producerworkbench.dto.response.ExplanationResponse;

/**
 * Service interface for AI-powered music term explanations
 * using RAG (Retrieval-Augmented Generation)
 */
public interface AiExplanationService {
    
    /**
     * Generate Vietnamese explanation for highlighted music production term
     * 
     * Flow:
     * 1. Vector search to find related terms
     * 2. Build context from search results
     * 3. Generate explanation using Gemini AI
     * 
     * @param request Explanation request containing highlighted text
     * @return Explanation response with Vietnamese explanation and related terms
     */
    ExplanationResponse generateExplanation(ExplanationRequest request);
}
