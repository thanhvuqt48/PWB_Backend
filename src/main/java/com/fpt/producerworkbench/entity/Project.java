package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.ProjectStatus;
import com.fpt.producerworkbench.common.ProjectType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "projects")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project  extends AbstractEntity<Long>{

    @Column(nullable = false, unique = true)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = true)
    private User client;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false)
    private ProjectType type;

    /**
     * KHÔNG khởi tạo = new ArrayList<>() tại field level.
     * Để Hibernate quản lý collection, tránh lỗi "Found shared references to a collection".
     * Sử dụng getter để khởi tạo lazy nếu cần.
     */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    private List<LiveSession> liveSessions;

    /**
     * Getter với lazy initialization - tránh NullPointerException
     */
    public List<LiveSession> getLiveSessions() {
        if (liveSessions == null) {
            liveSessions = new ArrayList<>();
        }
        return liveSessions;
    }

    /**
     * Setter mutate in-place để tránh shared reference
     */
    public void setLiveSessions(List<LiveSession> liveSessions) {
        if (this.liveSessions == null) {
            this.liveSessions = new ArrayList<>();
        }
        this.liveSessions.clear();
        if (liveSessions != null) {
            this.liveSessions.addAll(liveSessions);
        }
    }
}