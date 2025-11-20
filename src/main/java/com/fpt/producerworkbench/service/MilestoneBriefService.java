package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.request.MilestoneBriefUpsertRequest;
import com.fpt.producerworkbench.dto.response.MilestoneBriefDetailResponse;
import org.springframework.security.core.Authentication;

public interface MilestoneBriefService {

    MilestoneBriefDetailResponse upsertMilestoneBrief(Long projectId, Long milestoneId,
                                                      MilestoneBriefUpsertRequest request,
                                                      Authentication auth);

    MilestoneBriefDetailResponse getMilestoneBrief(Long projectId, Long milestoneId,
                                                   Authentication auth);

    MilestoneBriefDetailResponse upsertInternalMilestoneBrief(Long projectId, Long milestoneId,
                                                              MilestoneBriefUpsertRequest request,
                                                              Authentication auth);

    MilestoneBriefDetailResponse getInternalMilestoneBrief(Long projectId, Long milestoneId,
                                                           Authentication auth);

}
