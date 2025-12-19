package com.fpt.producerworkbench.common;

/**
 * Trạng thái chấm dứt hợp đồng
 */
public enum TerminationStatus {
    /**
     * Đang xử lý
     */
    PROCESSING,
    
    /**
     * Hoàn tất
     */
    COMPLETED,
    
    /**
     * Hoàn tất một phần (chờ lần 2 sau ngày 20)
     */
    PARTIAL_COMPLETED,
    
    /**
     * Thất bại
     */
    FAILED
}


