package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.AIContextRequest;
import com.fpt.producerworkbench.dto.response.AIContextualResponse;

/**
 * Service interface for AI-powered contextual guidance
 * Uses RAG (Retrieval-Augmented Generation) to provide step-by-step help
 */
public interface AIContextService {

    /**
     * Generate contextual guidance for user query
     * 
     * Flow:
     * 1. Analyze user intent (how-to, troubleshoot, find-feature, etc.)
     * 2. Vector search to find relevant guides
     * 3. Build context from guides
     * 4. Generate AI response with Gemini
     * 5. Return answer + related guides + suggested actions
     * 
     * @param request AI context request with user query
     * @return Contextual AI response with guidance
     */
    AIContextualResponse getContextualGuidance(AIContextRequest request);

    /**
     * Analyze user query intent
     * 
     * @param query User query
     * @return Intent type: "how-to", "troubleshoot", "find-feature", "best-practice"
     */
    String analyzeIntent(String query);

    /**
     * Generate quick help response (simpler, faster than full contextual guidance)
     * 
     * @param query Simple user query
     * @return Quick AI response
     */
    AIContextualResponse getQuickHelp(String query);
}
