package com.fpt.producerworkbench.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MilestoneBriefGroupRequest {

    @JsonProperty("id")
    Long id;

    @NotBlank
    @JsonProperty("title")
    String title;

    @JsonProperty("position")
    Integer position;

    @Valid
    @JsonProperty("blocks")
    List<MilestoneBriefBlockRequest> blocks;
}
