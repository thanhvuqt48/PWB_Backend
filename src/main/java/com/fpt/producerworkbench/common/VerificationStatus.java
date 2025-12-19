package com.fpt.producerworkbench.common;

/**
 * Trạng thái xác thực CCCD
 */
public enum VerificationStatus {
    /**
     * Chờ xác thực
     */
    PENDING,
    
    /**
     * Đã xác thực
     */
    VERIFIED,
    
    /**
     * Bị từ chối
     */
    REJECTED,
    
    /**
     * Hết hạn
     */
    EXPIRED
}


