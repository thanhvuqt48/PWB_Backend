package com.fpt.producerworkbench.configuration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient configuration with Advisors
 * - QuestionAnswerAdvisor: RAG (Retrieves guides from Pinecone)
 * - MessageChatMemoryAdvisor: Conversation history from Redis
 * 
 * Uses GeminiChatModel as the underlying ChatModel implementation
 */
@Configuration
public class AIChatClientConfig {
    
    @Bean
    public ChatClient aiChatClient(
            ChatModel geminiChatModel,
            VectorStore pineconeVectorStore,
            ChatMemory redisChatMemory) {
        
        return ChatClient.builder(geminiChatModel)
            .defaultAdvisors(
                // RAG: Search relevant guides from Pinecone
                new QuestionAnswerAdvisor(
                    pineconeVectorStore,
                    SearchRequest.defaults()
                        .withTopK(5)  // Get top 5 candidates
                        .withSimilarityThreshold(0.7)
                ),
                
                // Memory: Conversation history from Redis
                new MessageChatMemoryAdvisor(
                    redisChatMemory,
                    "default-session", // Will be overridden per request
                    10  // Keep last 10 messages (sliding window)
                )
            )
            .build();
    }
}
