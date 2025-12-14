package com.fpt.producerworkbench.common;

/**
 * Trạng thái thuế
 */
public enum TaxStatus {
    /**
     * Hoàn tất (trước ngày 20 hoặc lần 1 sau ngày 20)
     */
    COMPLETED,
    
    /**
     * Chờ hoàn thuế (lần 2 sau ngày 20)
     */
    WAITING_REFUND,
    
    /**
     * Đã hoàn thuế
     */
    REFUNDED
}


