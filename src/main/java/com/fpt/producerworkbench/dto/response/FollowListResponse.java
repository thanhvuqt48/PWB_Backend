package com.fpt.producerworkbench.dto.response;

import lombok.*;
import org.springframework.data.domain.Page;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowListResponse {
    private Page<FollowResponse> page;
    private long totalFollowers;
    private long totalFollowing;
}
