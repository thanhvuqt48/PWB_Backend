package com.fpt.producerworkbench.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class CompleteAndSuggestRequest {
    Integer waitSeconds;
}