package com.fpt.producerworkbench.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "pinecone")
public class PineconeProperties {
    private String apiKey;
    private String environment;
    private String indexName;
    private String projectId;
    private String namespace = "music-terms";
    private String host;
    private Integer dimension = 768;
    private String metric = "cosine";
    private Integer topK = 5;
}
