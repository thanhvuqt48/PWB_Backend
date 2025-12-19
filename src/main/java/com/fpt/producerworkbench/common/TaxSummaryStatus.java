package com.fpt.producerworkbench.common;

/**
 * Trạng thái tổng hợp thuế
 */
public enum TaxSummaryStatus {
    /**
     * Nháp
     */
    DRAFT,
    
    /**
     * Đã hoàn thiện
     */
    FINALIZED,
    
    /**
     * Đã kê khai
     */
    DECLARED,
    
    /**
     * Đã nộp thuế
     */
    PAID
}


