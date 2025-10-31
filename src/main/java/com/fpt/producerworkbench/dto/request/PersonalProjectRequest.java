package com.fpt.producerworkbench.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PersonalProjectRequest {
    private String title;
    private String description;
    private String audioDemoUrl;
    private String coverImageUrl;
    private int releaseYear;
}
