package com.fpt.producerworkbench.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpotifyLinkInfoResponse {
    private String linkType;
    private List<String> genres;
    private String spotifyId;
    private String artistName;
    private String artistImageUrl;
}

