package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.TrackUploadCompleteRequest;
import com.fpt.producerworkbench.dto.request.TrackUploadUrlRequest;
import com.fpt.producerworkbench.dto.response.TrackListItemResponse;
import com.fpt.producerworkbench.dto.response.TrackSuggestionResponse;
import com.fpt.producerworkbench.dto.response.TrackPresignedUrlResponse;
import com.fpt.producerworkbench.dto.response.TrackUploadDirectResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TrackService {
    TrackPresignedUrlResponse generateUploadUrl(Long userId, TrackUploadUrlRequest req);
    Long uploadComplete(Long userId, TrackUploadCompleteRequest req);

    TrackUploadDirectResponse uploadDirect(Long userId, Long projectId, MultipartFile file, String mimeType);

    TrackUploadDirectResponse uploadDirectRaw(Long userId, Long projectId, byte[] data, String filename, String mimeType);

    TrackSuggestionResponse completeAndSuggest(Long userId, Long trackId, int waitSeconds);

    TrackSuggestionResponse getSuggestion(Long userId, Long trackId);
    void resuggest(Long userId, Long trackId);
    void deleteTrack(Long userId, Long trackId);
    List<TrackListItemResponse> getTracksByProject(Long userId, Long projectId);
}
