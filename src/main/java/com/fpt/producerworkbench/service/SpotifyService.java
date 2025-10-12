package com.fpt.producerworkbench.service;

import java.util.List;

public interface SpotifyService {
    List<String> getGenresFromTrackLink(String trackLink);
}
