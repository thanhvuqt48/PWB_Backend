package com.fpt.producerworkbench.entity.userguide;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entity representing a step in a user guide
 * Each guide can have multiple steps in sequential order
 */
@Entity
@Table(name = "guide_steps", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"guide_id", "step_order"}),
       indexes = {
           @Index(name = "idx_guide_id", columnList = "guide_id"),
           @Index(name = "idx_step_order", columnList = "guide_id, step_order")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class GuideStep {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guide_id", nullable = false)
    private UserGuide userGuide;
    
    // Step Info
    @Column(nullable = false)
    private Integer stepOrder;
    
    @Column(nullable = false, length = 255)
    private String title;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;
    
    // UI Context
    @Column(length = 255)
    private String screenLocation; // e.g., /projects, /tracks
    
    @Column(length = 255)
    private String uiElement; // e.g., "Sidebar > Projects menu"
    
    @Column(columnDefinition = "TEXT")
    private String expectedResult;
    
    // Visual Aids
    @Column(length = 500)
    private String screenshotUrl;
    
    @Column(length = 500)
    private String videoUrl;
    
    // Tips & Warnings
    @Column(columnDefinition = "TEXT")
    private String tips;
    
    @Column(columnDefinition = "TEXT")
    private String commonMistakes;
    
    // Audit
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
