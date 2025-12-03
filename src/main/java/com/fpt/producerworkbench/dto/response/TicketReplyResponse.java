package com.fpt.producerworkbench.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TicketReplyResponse {
    private Long id;
    private Long ticketId;
    private String content;
    private String senderName;
    private String senderRole;
    private LocalDateTime createdAt;
    private List<String> attachmentUrls;
}