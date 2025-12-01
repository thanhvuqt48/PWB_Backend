package com.fpt.producerworkbench.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class TicketReplyRequest {
    private String content;

    private List<String> attachmentUrls;
}