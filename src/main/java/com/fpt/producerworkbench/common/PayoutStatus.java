package com.fpt.producerworkbench.common;

/**
 * Trạng thái giải ngân
 */
public enum PayoutStatus {
    /**
     * Đang chờ xử lý
     */
    PENDING,
    
    /**
     * Hoàn tất
     */
    COMPLETED,
    
    /**
     * Thất bại
     */
    FAILED,
    
    /**
     * Đã đảo ngược
     */
    REVERSED
}


