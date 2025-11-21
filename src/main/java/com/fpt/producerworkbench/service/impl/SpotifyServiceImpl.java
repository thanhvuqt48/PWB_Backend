package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.dto.response.SpotifyLinkInfoResponse;
import com.fpt.producerworkbench.service.SpotifyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.credentials.ClientCredentials;
import se.michaelthelin.spotify.model_objects.specification.Album;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.authorization.client_credentials.ClientCredentialsRequest;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class SpotifyServiceImpl implements SpotifyService {

    private final SpotifyApi spotifyApi;
    private static final Pattern TRACK_ID_PATTERN = Pattern.compile("open\\.spotify\\.com/track/([a-zA-Z0-9]+)");
    private static final Pattern ARTIST_ID_PATTERN = Pattern.compile("open\\.spotify\\.com/artist/([a-zA-Z0-9]+)");
    private static final Pattern ALBUM_ID_PATTERN = Pattern.compile("open\\.spotify\\.com/album/([a-zA-Z0-9]+)");

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

            // Kiểm tra loại link và xử lý tương ứng
            String trackId = extractTrackIdFromUrl(trackLink);
            if (trackId != null) {
                return getGenresFromTrack(trackId, trackLink);
            }

            String artistId = extractArtistIdFromUrl(trackLink);
            if (artistId != null) {
                return getGenresFromArtist(artistId, trackLink);
            }

            String albumId = extractAlbumIdFromUrl(trackLink);
            if (albumId != null) {
                return getGenresFromAlbum(albumId, trackLink);
            }

            log.warn("Không thể trích xuất ID từ link (không phải track/artist/album): {}", trackLink);
            return Collections.emptyList();

        } catch (Exception e) {
            log.error("Lỗi khi lấy dữ liệu từ Spotify API cho link {}: {}", trackLink, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> getGenresFromTrack(String trackId, String originalLink) {
        try {
            Track track = spotifyApi.getTrack(trackId).build().execute();
            if (track == null || track.getArtists() == null || track.getArtists().length == 0) {
                return Collections.emptyList();
            }

            String artistId = track.getArtists()[0].getId();
            Artist artistDetails = spotifyApi.getArtist(artistId).build().execute();
            if (artistDetails == null || artistDetails.getGenres() == null) {
                return Collections.emptyList();
            }

            log.info("Tìm thấy các genres từ track link {}: {}", originalLink, Arrays.toString(artistDetails.getGenres()));
            return Arrays.asList(artistDetails.getGenres());
        } catch (Exception e) {
            log.error("Lỗi khi lấy genres từ track {}: {}", trackId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> getGenresFromArtist(String artistId, String originalLink) {
        try {
            Artist artist = spotifyApi.getArtist(artistId).build().execute();
            if (artist == null || artist.getGenres() == null || artist.getGenres().length == 0) {
                return Collections.emptyList();
            }

            log.info("Tìm thấy các genres từ artist link {}: {}", originalLink, Arrays.toString(artist.getGenres()));
            return Arrays.asList(artist.getGenres());
        } catch (Exception e) {
            log.error("Lỗi khi lấy genres từ artist {}: {}", artistId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> getGenresFromAlbum(String albumId, String originalLink) {
        try {
            Album album = spotifyApi.getAlbum(albumId).build().execute();
            if (album == null || album.getArtists() == null || album.getArtists().length == 0) {
                return Collections.emptyList();
            }

            // Lấy genres từ tất cả các artists trong album
            Set<String> allGenres = new HashSet<>();
            for (ArtistSimplified artistSimplified : album.getArtists()) {
                try {
                    Artist artist = spotifyApi.getArtist(artistSimplified.getId()).build().execute();
                    if (artist != null && artist.getGenres() != null) {
                        allGenres.addAll(Arrays.asList(artist.getGenres()));
                    }
                } catch (Exception e) {
                    log.warn("Không thể lấy genres từ artist {} trong album: {}", artistSimplified.getId(), e.getMessage());
                }
            }

            if (allGenres.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> genresList = new ArrayList<>(allGenres);
            log.info("Tìm thấy các genres từ album link {}: {}", originalLink, genresList);
            return genresList;
        } catch (Exception e) {
            log.error("Lỗi khi lấy genres từ album {}: {}", albumId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String extractTrackIdFromUrl(String url) {
        Matcher matcher = TRACK_ID_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractArtistIdFromUrl(String url) {
        Matcher matcher = ARTIST_ID_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractAlbumIdFromUrl(String url) {
        Matcher matcher = ALBUM_ID_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    @Override
    public SpotifyLinkInfoResponse getSpotifyLinkInfo(String spotifyLink) {
        try {
            refreshAccessToken();

            // Kiểm tra loại link và xử lý tương ứng
            String trackId = extractTrackIdFromUrl(spotifyLink);
            if (trackId != null) {
                return getTrackLinkInfo(trackId, spotifyLink);
            }

            String artistId = extractArtistIdFromUrl(spotifyLink);
            if (artistId != null) {
                return getArtistLinkInfo(artistId, spotifyLink);
            }

            String albumId = extractAlbumIdFromUrl(spotifyLink);
            if (albumId != null) {
                return getAlbumLinkInfo(albumId, spotifyLink);
            }

            log.warn("Không thể trích xuất ID từ link (không phải track/artist/album): {}", spotifyLink);
            return SpotifyLinkInfoResponse.builder()
                    .linkType("UNKNOWN")
                    .genres(Collections.emptyList())
                    .build();

        } catch (Exception e) {
            log.error("Lỗi khi lấy thông tin từ Spotify API cho link {}: {}", spotifyLink, e.getMessage());
            return SpotifyLinkInfoResponse.builder()
                    .linkType("ERROR")
                    .genres(Collections.emptyList())
                    .build();
        }
    }

    private SpotifyLinkInfoResponse getTrackLinkInfo(String trackId, String originalLink) {
        try {
            Track track = spotifyApi.getTrack(trackId).build().execute();
            if (track == null || track.getArtists() == null || track.getArtists().length == 0) {
                return SpotifyLinkInfoResponse.builder()
                        .linkType("TRACK")
                        .spotifyId(trackId)
                        .genres(Collections.emptyList())
                        .build();
            }

            String artistId = track.getArtists()[0].getId();
            Artist artist = spotifyApi.getArtist(artistId).build().execute();
            List<String> genres = artist != null && artist.getGenres() != null 
                    ? Arrays.asList(artist.getGenres()) 
                    : Collections.emptyList();
            
            String artistName = artist != null ? artist.getName() : null;
            String artistImageUrl = getArtistImageUrl(artist);

            return SpotifyLinkInfoResponse.builder()
                    .linkType("TRACK")
                    .spotifyId(trackId)
                    .genres(genres)
                    .artistName(artistName)
                    .artistImageUrl(artistImageUrl)
                    .build();
        } catch (Exception e) {
            log.error("Lỗi khi lấy thông tin track {}: {}", trackId, e.getMessage());
            return SpotifyLinkInfoResponse.builder()
                    .linkType("TRACK")
                    .spotifyId(trackId)
                    .genres(Collections.emptyList())
                    .build();
        }
    }

    private SpotifyLinkInfoResponse getArtistLinkInfo(String artistId, String originalLink) {
        try {
            Artist artist = spotifyApi.getArtist(artistId).build().execute();
            if (artist == null) {
                return SpotifyLinkInfoResponse.builder()
                        .linkType("ARTIST")
                        .spotifyId(artistId)
                        .genres(Collections.emptyList())
                        .build();
            }

            List<String> genres = artist.getGenres() != null && artist.getGenres().length > 0
                    ? Arrays.asList(artist.getGenres())
                    : Collections.emptyList();
            
            String artistName = artist.getName();
            String artistImageUrl = getArtistImageUrl(artist);

            return SpotifyLinkInfoResponse.builder()
                    .linkType("ARTIST")
                    .spotifyId(artistId)
                    .genres(genres)
                    .artistName(artistName)
                    .artistImageUrl(artistImageUrl)
                    .build();
        } catch (Exception e) {
            log.error("Lỗi khi lấy thông tin artist {}: {}", artistId, e.getMessage());
            return SpotifyLinkInfoResponse.builder()
                    .linkType("ARTIST")
                    .spotifyId(artistId)
                    .genres(Collections.emptyList())
                    .build();
        }
    }

    private SpotifyLinkInfoResponse getAlbumLinkInfo(String albumId, String originalLink) {
        try {
            Album album = spotifyApi.getAlbum(albumId).build().execute();
            if (album == null || album.getArtists() == null || album.getArtists().length == 0) {
                return SpotifyLinkInfoResponse.builder()
                        .linkType("ALBUM")
                        .spotifyId(albumId)
                        .genres(Collections.emptyList())
                        .build();
            }

            // Lấy genres từ tất cả các artists trong album
            Set<String> allGenres = new HashSet<>();
            String mainArtistId = album.getArtists()[0].getId();
            Artist mainArtist = null;

            for (ArtistSimplified artistSimplified : album.getArtists()) {
                try {
                    Artist artist = spotifyApi.getArtist(artistSimplified.getId()).build().execute();
                    if (artist != null && artist.getGenres() != null) {
                        allGenres.addAll(Arrays.asList(artist.getGenres()));
                        if (artistSimplified.getId().equals(mainArtistId)) {
                            mainArtist = artist;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Không thể lấy genres từ artist {} trong album: {}", artistSimplified.getId(), e.getMessage());
                }
            }

            List<String> genresList = new ArrayList<>(allGenres);
            String artistName = mainArtist != null ? mainArtist.getName() : 
                    (album.getArtists().length > 0 ? album.getArtists()[0].getName() : null);
            String artistImageUrl = getArtistImageUrl(mainArtist);

            return SpotifyLinkInfoResponse.builder()
                    .linkType("ALBUM")
                    .spotifyId(albumId)
                    .genres(genresList)
                    .artistName(artistName)
                    .artistImageUrl(artistImageUrl)
                    .build();
        } catch (Exception e) {
            log.error("Lỗi khi lấy thông tin album {}: {}", albumId, e.getMessage());
            return SpotifyLinkInfoResponse.builder()
                    .linkType("ALBUM")
                    .spotifyId(albumId)
                    .genres(Collections.emptyList())
                    .build();
        }
    }

    private String getArtistImageUrl(Artist artist) {
        if (artist == null || artist.getImages() == null || artist.getImages().length == 0) {
            return null;
        }
        
        // Lấy ảnh có kích thước trung bình (thường là index 1) hoặc ảnh đầu tiên
        // Spotify API trả về ảnh theo thứ tự từ lớn đến nhỏ
        // Thường lấy ảnh có kích thước trung bình (index 1) hoặc nhỏ nhất (index cuối)
        // Nếu muốn ảnh lớn nhất thì lấy index 0
        int imageIndex = artist.getImages().length > 1 ? 1 : 0;
        return artist.getImages()[imageIndex].getUrl();
    }

    private void refreshAccessToken() throws Exception {
        ClientCredentialsRequest request = spotifyApi.clientCredentials().build();
        ClientCredentials credentials = request.execute();
        spotifyApi.setAccessToken(credentials.getAccessToken());
    }
}