package com.fpt.producerworkbench.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TicketResponse {
    private Long id;
    private String title;
    private String status;
    private String createdBy;
    private String projectName;
    private LocalDateTime createdAt;
    private List<String> attachmentUrls;

    private String description;
}