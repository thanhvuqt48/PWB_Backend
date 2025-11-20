package com.fpt.producerworkbench.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.PineconeVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class PineconeVectorStoreConfig {

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.vectorstore.pinecone", name = "apiKey")
    public VectorStore vectorStore(
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.pinecone.apiKey}") String apiKey,
            @Value("${spring.ai.vectorstore.pinecone.indexName}") String indexName,
            @Value("${spring.ai.vectorstore.pinecone.namespace}") String namespace,
            @Value("${spring.ai.vectorstore.pinecone.projectName}") String projectName,
            @Value("${spring.ai.vectorstore.pinecone.environment}") String environment) {
        
        log.info("🗂️ Creating Pinecone VectorStore");
        log.info("   Index: {}", indexName);
        log.info("   Namespace: {}", namespace);
        log.info("   Project: {}", projectName);
        log.info("   Environment: {}", environment);
        log.info("   EmbeddingModel: {}", embeddingModel.getClass().getSimpleName());
        
        PineconeVectorStore.PineconeVectorStoreConfig config = 
                PineconeVectorStore.PineconeVectorStoreConfig.builder()
                        .withApiKey(apiKey)
                        .withIndexName(indexName)
                        .withNamespace(namespace)
                        .withProjectId(projectName)
                        .withEnvironment(environment)
                        .build();
        
        return new PineconeVectorStore(config, embeddingModel);
    }
}
