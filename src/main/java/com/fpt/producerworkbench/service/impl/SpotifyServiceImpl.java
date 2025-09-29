package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.service.SpotifyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.credentials.ClientCredentials;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.authorization.client_credentials.ClientCredentialsRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class SpotifyServiceImpl implements SpotifyService {

    private final SpotifyApi spotifyApi;
    private static final Pattern trackIdPattern = Pattern.compile("open\\.spotify\\.com/track/([a-zA-Z0-9]+)");

    public SpotifyServiceImpl(@Value("${spotify.client-id}") String clientId,
                              @Value("${spotify.client-secret}") String clientSecret) {
        this.spotifyApi = new SpotifyApi.Builder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .build();
    }

    @Override
    public List<String> getGenresFromTrackLink(String trackLink) {
        try {
            refreshAccessToken();
            String trackId = extractTrackId(trackLink);
            if (trackId == null) {
                log.warn("Invalid Spotify track link: {}", trackLink);
                return Collections.emptyList();
            }

            Track track = spotifyApi.getTrack(trackId).build().execute();
            if (track == null) return Collections.emptyList();

            ArtistSimplified[] artists = track.getArtists();
            if (artists == null || artists.length == 0) return Collections.emptyList();

            String artistId = artists[0].getId();
            var artistDetails = spotifyApi.getArtist(artistId).build().execute();
            if (artistDetails == null || artistDetails.getGenres() == null) return Collections.emptyList();

            log.info("Found genres for track link {}: {}", trackLink, Arrays.toString(artistDetails.getGenres()));
            return Arrays.asList(artistDetails.getGenres());

        } catch (Exception e) {
            log.error("Error fetching data from Spotify API", e);
            return Collections.emptyList();
        }
    }

    private String extractTrackId(String url) {
        Matcher matcher = trackIdPattern.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private void refreshAccessToken() throws Exception {
        ClientCredentialsRequest req = spotifyApi.clientCredentials().build();
        ClientCredentials credentials = req.execute();
        spotifyApi.setAccessToken(credentials.getAccessToken());
    }
}
