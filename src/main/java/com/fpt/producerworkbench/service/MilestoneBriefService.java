package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.MilestoneBriefUpsertRequest;
import com.fpt.producerworkbench.dto.response.MilestoneBriefDetailResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

public interface MilestoneBriefService {

    MilestoneBriefDetailResponse upsertMilestoneBrief(Long projectId, Long milestoneId,
                                                      MilestoneBriefUpsertRequest request,
                                                      Authentication auth);

    MilestoneBriefDetailResponse getMilestoneBrief(Long projectId, Long milestoneId,
                                                   Authentication auth);

    void deleteExternalMilestoneBrief(Long projectId, Long milestoneId, Authentication auth);

    MilestoneBriefDetailResponse upsertInternalMilestoneBrief(Long projectId, Long milestoneId,
                                                              MilestoneBriefUpsertRequest request,
                                                              Authentication auth);

    MilestoneBriefDetailResponse getInternalMilestoneBrief(Long projectId, Long milestoneId,
                                                           Authentication auth);

    void deleteInternalMilestoneBrief(Long projectId, Long milestoneId, Authentication auth);

    String uploadBriefFile(Long projectId, Long milestoneId, MultipartFile file, String type, Authentication auth);
}
