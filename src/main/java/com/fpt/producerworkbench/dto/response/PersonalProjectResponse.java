package com.fpt.producerworkbench.dto.response;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PersonalProjectResponse {

    private Long id;
    private String title;
    private String description;
    private String audioDemoUrl;
    private String coverImageUrl;
    private int releaseYear;
}
