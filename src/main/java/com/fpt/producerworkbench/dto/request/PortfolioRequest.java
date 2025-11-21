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
public class PortfolioRequest {

    private String customUrlSlug;
    private String headline;
    private Double latitude;
    private Double longitude;
    private List<Long> genreIds;
    private List<String> tags;

    @Valid
    private List<PortfolioSectionRequest> sections;

    @Valid
    private List<PersonalProjectRequest> personalProjects;

    @Valid
    private List<SocialLinkRequest> socialLinks;

}
