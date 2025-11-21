package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO cho việc tải về ZIP các track bản gốc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadOriginalTracksZipResponse {
    
    /**
     * Presigned URL để tải về file ZIP (có thời hạn 15 phút)
     */
    private String downloadUrl;
    
    /**
     * Tên file ZIP
     */
    private String zipFileName;
    
    /**
     * Thời gian hết hạn của download URL
     */
    private LocalDateTime expiresAt;
    
    /**
     * Thống kê về quá trình tạo ZIP
     */
    private ZipStatistics statistics;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ZipStatistics {
        /**
         * Tổng số tracks được xử lý
         */
        private Integer totalTracks;
        
        /**
         * Số tracks thành công (đã thêm vào ZIP)
         */
        private Integer successfulTracks;
        
        /**
         * Số tracks bị lỗi (không thể download hoặc không hợp lệ)
         */
        private Integer failedTracks;
        
        /**
         * Danh sách track IDs bị lỗi
         */
        private List<Long> failedTrackIds;
    }
}

