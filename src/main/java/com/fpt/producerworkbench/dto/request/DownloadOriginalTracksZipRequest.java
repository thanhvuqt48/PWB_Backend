package com.fpt.producerworkbench.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO cho việc tải về ZIP các track bản gốc
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadOriginalTracksZipRequest {
    
    /**
     * Danh sách track IDs muốn tải về (optional)
     * Nếu không có hoặc rỗng → tải tất cả tracks đã gửi cho client
     */
    private List<Long> trackIds;
}

