package com.fpt.producerworkbench.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fpt.producerworkbench.common.MilestoneBriefScope;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneBriefDetailResponse {

    @JsonProperty("milestoneId")
    private Long milestoneId;

    @JsonProperty("projectId")
    private Long projectId;

    @JsonProperty("scope")
    private MilestoneBriefScope scope;

    @JsonProperty("groups")
    private List<MilestoneBriefGroupResponse> groups;
}
