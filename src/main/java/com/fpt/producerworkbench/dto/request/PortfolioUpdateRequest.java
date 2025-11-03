package com.fpt.producerworkbench.dto.request;

import jakarta.validation.Valid;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Setter
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PortfolioUpdateRequest {

    private Long id;
    private String customUrlSlug;
    private String headline;
    private Double latitude;
    private Double longitude;
    private List<Long> genreIds;
    private List<String> tags;

    @Valid
    private List<PortfolioSectionUpdateRequest> sections;

    @Valid
    private List<PersonalProjectUpdateRequest> personalProjects;

    @Valid
    private List<SocialLinkUpdateRequest> socialLinks;

}
