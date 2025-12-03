package com.fpt.producerworkbench.dto.response;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.Set;

@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PortfolioResponse {

    Long id;
    Long userId;
    String firstName;
    String lastName;
    String avatarUrl;
    String customUrlSlug;
    String headline;
    String coverImageUrl;
    boolean isPublic;
    Double latitude;
    Double longitude;
    Set<String> genres;
    Set<String> tags;
    Set<PortfolioSectionResponse> sections;
    Set<PersonalProjectResponse> personalProjects;
    Set<SocialLinkResponse> socialLinks;

    String role;
}
