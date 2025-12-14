package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO chứa cả danh sách milestones và tracks của từng milestone
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionMilestonesWithTracksResponse {
    
    /**
     * Danh sách milestones
     */
    private List<MilestoneWithTracksResponse> milestones;
}

