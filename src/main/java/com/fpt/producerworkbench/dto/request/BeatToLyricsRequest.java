package com.fpt.producerworkbench.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class BeatToLyricsRequest {
    private Long projectId;
    private String topic;
    private List<String> keywords;
    private String mood;

    private List<String> structure;

    private Double temperature;
}