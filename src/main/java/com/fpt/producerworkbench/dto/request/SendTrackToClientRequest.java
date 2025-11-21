package com.fpt.producerworkbench.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO cho việc gửi track cho khách hàng
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendTrackToClientRequest {
    
    /**
     * Ghi chú khi gửi track (optional)
     */
    private String note;
}

