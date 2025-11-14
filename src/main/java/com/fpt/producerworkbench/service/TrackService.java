package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.TrackUploadCompleteRequest;
import com.fpt.producerworkbench.dto.request.TrackUploadUrlRequest;
import com.fpt.producerworkbench.dto.response.TrackSuggestionResponse;
import com.fpt.producerworkbench.dto.response.TrackUploadUrlResponse;

public interface TrackService {
    TrackUploadUrlResponse generateUploadUrl(Long userId, TrackUploadUrlRequest req);
    Long uploadComplete(Long userId, TrackUploadCompleteRequest req);

    TrackSuggestionResponse getSuggestion(Long userId, Long trackId);
    void resuggest(Long userId, Long trackId);
    void deleteTrack(Long userId, Long trackId);
}
