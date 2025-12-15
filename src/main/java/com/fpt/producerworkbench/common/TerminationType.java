package com.fpt.producerworkbench.common;

/**
 * Loại chấm dứt hợp đồng theo thời điểm
 */
public enum TerminationType {
    /**
     * Chấm dứt trước ngày 20 của tháng (chưa kê khai thuế)
     */
    BEFORE_DAY_20,
    
    /**
     * Chấm dứt sau ngày 20 của tháng (đã kê khai thuế)
     */
    AFTER_DAY_20
}


