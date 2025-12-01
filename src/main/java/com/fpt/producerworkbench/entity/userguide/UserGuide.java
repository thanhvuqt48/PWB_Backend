package com.fpt.producerworkbench.entity.userguide;

import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing an AI-powered user guide
 * Stored in PostgreSQL for metadata and relationships
 * Vectors stored in Pinecone for semantic search
 */
@Entity
@Table(name = "user_guides", indexes = {
        @Index(name = "idx_category", columnList = "category"),
        @Index(name = "idx_difficulty", columnList = "difficulty"),
        @Index(name = "idx_is_active", columnList = "is_active"),
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_pinecone_vector_id", columnList = "pinecone_vector_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class UserGuide {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // Basic Info
    @Column(nullable = false, length = 255)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String shortDescription;
    
    @Column(nullable = false, length = 100)
    @Enumerated(EnumType.STRING)
    private GuideCategory category;
    
    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private GuideDifficulty difficulty;
    
    // Content
    @Column(nullable = false, columnDefinition = "TEXT")
    private String contentText;
    
    // Prerequisites (stored as JSON array)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> prerequisites = new ArrayList<>();
    
    // Metadata (PostgreSQL arrays)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] tags;
    
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] keywords;
    
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "bigint[]")
    private Long[] relatedGuideIds;
    
    // Searchable queries - Common questions users might ask
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] searchableQueries;
    
    // Images (S3 URLs)
    @Column(length = 500)
    private String coverImageUrl;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<StepImage> stepImages = new ArrayList<>();
    
    // Pinecone Reference
    @Column(nullable = false, unique = true, length = 100)
    private String pineconeVectorId;
    
    @Column(length = 50)
    private String pineconeNamespace = "user-guides";
    
    // Stats
    @Column(nullable = false)
    @Builder.Default
    private Integer viewCount = 0;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer helpfulCount = 0;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer unhelpfulCount = 0;
    
    // Audit
    @Column(length = 100)
    private String author;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(length = 20)
    @Builder.Default
    private String version = "1.0";
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    // Relationships
    @OneToMany(mappedBy = "userGuide", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepOrder ASC")
    @Builder.Default
    private List<GuideStep> steps = new ArrayList<>();
    
    // Helper methods
    public void addStep(GuideStep step) {
        steps.add(step);
        step.setUserGuide(this);
    }
    
    public void removeStep(GuideStep step) {
        steps.remove(step);
        step.setUserGuide(null);
    }
    
    public void incrementViewCount() {
        this.viewCount++;
    }
    
    public void incrementHelpfulCount() {
        this.helpfulCount++;
    }
    
    public void incrementUnhelpfulCount() {
        this.unhelpfulCount++;
    }
    
    /**
     * Inner class for step images JSON
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepImage {
        private Integer step;
        private String url;
    }
}
