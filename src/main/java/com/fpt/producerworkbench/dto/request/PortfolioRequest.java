package com.fpt.producerworkbench.dto.request;

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
    private List<PortfolioSectionRequest> sections;
    private List<PersonalProjectRequest> personalProjects;
    private List<SocialLinkRequest> socialLinks;

}
