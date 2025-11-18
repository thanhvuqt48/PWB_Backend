package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpotifyRecommendationResponse {
    private SpotifyLinkInfoResponse linkInfo;
    private Page<ProducerSummaryResponse> producers;
}

