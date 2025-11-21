package com.fpt.producerworkbench.dto.response;

import com.fpt.producerworkbench.common.ClientDeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Response DTO cho client delivery
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientDeliveryResponse {
    
    /**
     * ID của delivery
     */
    private Long id;
    
    /**
     * ID của track
     */
    private Long trackId;
    
    /**
     * Tên track
     */
    private String trackName;
    
    /**
     * ID của milestone
     */
    private Long milestoneId;
    
    /**
     * ID của user gửi
     */
    private Long sentBy;
    
    /**
     * Tên user gửi
     */
    private String sentByName;
    
    /**
     * Status của delivery
     */
    private ClientDeliveryStatus status;
    
    /**
     * Thời điểm gửi
     */
    private Date sentAt;
    
    /**
     * Ghi chú
     */
    private String note;
    
    /**
     * Số lượt gửi còn lại của milestone
     */
    private Integer productCountRemaining;
}

