package com.fpt.producerworkbench.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;

@Getter
@Builder
public class ProducerSummaryResponse {
    private Long userId;
    private String fullName;
    private String headline;
    private String avatarUrl;
    private String location;
    private Set<String> genres;
    private Set<String> tags;
    private Double distanceInKm;
}
