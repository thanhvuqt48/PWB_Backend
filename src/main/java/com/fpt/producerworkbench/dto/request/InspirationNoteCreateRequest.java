package com.fpt.producerworkbench.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Setter @Getter @Builder
@NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InspirationNoteCreateRequest {

    @JsonProperty("title")
    String title;

    @NotBlank
    @JsonProperty("content")
    String content;
}