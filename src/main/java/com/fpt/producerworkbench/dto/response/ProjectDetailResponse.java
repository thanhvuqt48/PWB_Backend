package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.common.ProjectStatus;
import com.fpt.producerworkbench.common.ProjectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDetailResponse {
    private Long id;
    private String title;
    private String description;
    private ProjectStatus status;
    private ProjectType type;
    
    // Thông tin người tạo
    private String creatorName;
    private String creatorAvatarUrl;
    
    // Thời gian
    private Date createdAt;
    private LocalDateTime startDate;
    private LocalDateTime completedAt;
}
