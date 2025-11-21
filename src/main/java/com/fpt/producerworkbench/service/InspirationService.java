package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.common.InspirationType;
import com.fpt.producerworkbench.dto.request.InspirationNoteCreateRequest;
import com.fpt.producerworkbench.dto.response.InspirationItemResponse;
import com.fpt.producerworkbench.dto.response.InspirationListResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface InspirationService {
    InspirationItemResponse uploadAsset(Long projectId, Long uploaderId,
                                        InspirationType type, MultipartFile file);

    InspirationItemResponse createNote(Long projectId, Long uploaderId,
                                       InspirationNoteCreateRequest request);

    InspirationListResponse<InspirationItemResponse> list(Long projectId,
                                                          InspirationType type,
                                                          Pageable pageable,
                                                          Long requesterId);

    void delete(Long projectId, Long itemId, Long requesterId);

    String getViewUrl(Long projectId, Long itemId, Long requesterId);

    String getDownloadUrl(Long projectId, Long itemId, String originalFileName, Long requesterId);
}