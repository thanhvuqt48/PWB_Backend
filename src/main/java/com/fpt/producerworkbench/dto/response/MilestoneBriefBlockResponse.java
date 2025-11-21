package com.fpt.producerworkbench.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fpt.producerworkbench.common.MilestoneBriefBlockType;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneBriefBlockResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("type")
    private MilestoneBriefBlockType type;

    @JsonProperty("label")
    private String label;

    @JsonProperty("content")
    private String content;

    @JsonProperty("position")
    private Integer position;
}
