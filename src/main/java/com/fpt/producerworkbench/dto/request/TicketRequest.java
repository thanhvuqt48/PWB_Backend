package com.fpt.producerworkbench.dto.request;

import com.fpt.producerworkbench.common.TicketPriority;
import lombok.Data;

import java.util.List;

@Data
public class TicketRequest {
    private String title;
    private String content;
    private List<String> attachmentUrls;
    private Long projectId;
}