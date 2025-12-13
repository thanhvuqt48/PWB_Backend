package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO cho track trong live session
 * File nhạc trả về sẽ tự động có voice tag nếu track có bật voice tag,
 * nếu không thì là bản gốc (giống như hiển thị trong phòng nội bộ/khách hàng)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionTrackResponse {
    
    /**
     * ID của track
     */
    private Long trackId;
    
    /**
     * Tên track
     */
    private String trackName;
    
    /**
     * Version của track
     */
    private String version;
    
    /**
     * HLS playback URL - đã tự động có voice tag nếu có, không thì bản gốc
     */
    private String hlsPlaybackUrl;
    
    /**
     * Thời lượng track (seconds)
     */
    private Integer duration;
    
    /**
     * Loại phòng: "INTERNAL" hoặc "CLIENT"
     */
    private String roomType;
    
    /**
     * Có bật voice tag hay không
     */
    private Boolean voiceTagEnabled;
}

