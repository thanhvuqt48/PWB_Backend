package com.fpt.producerworkbench.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fpt.producerworkbench.common.MilestoneBriefBlockType;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MilestoneBriefBlockRequest {

    @NotNull
    @JsonProperty("type")
    MilestoneBriefBlockType type;

    @JsonProperty("label")
    String label;

    @JsonProperty("content")
    String content;

    @JsonProperty("position")
    Integer position;
}
