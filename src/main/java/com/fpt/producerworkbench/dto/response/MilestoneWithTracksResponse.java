package com.fpt.producerworkbench.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fpt.producerworkbench.common.MilestoneStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * Response DTO cho milestone kèm danh sách tracks từ phòng nội bộ và phòng khách hàng
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneWithTracksResponse {
    
    @JsonProperty("id")
    private Long id;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("status")
    private MilestoneStatus status;
    
    @JsonProperty("sequence")
    private Integer sequence;
    
    @JsonProperty("projectTitle")
    private String projectTitle;
    
    @JsonProperty("createdAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Date createdAt;
    
    @JsonProperty("updatedAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Date updatedAt;
    
    /**
     * Danh sách tracks từ phòng nội bộ
     */
    @JsonProperty("internalTracks")
    private List<SessionTrackResponse> internalTracks;
    
    /**
     * Danh sách tracks từ phòng khách hàng
     */
    @JsonProperty("clientTracks")
    private List<SessionTrackResponse> clientTracks;
}

