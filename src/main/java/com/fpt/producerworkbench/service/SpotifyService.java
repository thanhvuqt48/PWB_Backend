package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.response.SpotifyLinkInfoResponse;

import java.util.List;

public interface SpotifyService {
    List<String> getGenresFromTrackLink(String trackLink);
    
    SpotifyLinkInfoResponse getSpotifyLinkInfo(String spotifyLink);
}
