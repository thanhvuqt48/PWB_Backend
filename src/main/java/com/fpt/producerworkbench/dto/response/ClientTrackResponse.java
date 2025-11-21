package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Response DTO cho track trong Client Room
 * Bao gồm thông tin track và delivery
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientTrackResponse {
    
    /**
     * Thông tin track
     */
    private TrackResponse track;
    
    /**
     * Thông tin delivery
     */
    private ClientDeliveryResponse delivery;
    
    /**
     * Thời điểm gửi (duplicate từ delivery để tiện sort)
     */
    private Date sentAt;
}

