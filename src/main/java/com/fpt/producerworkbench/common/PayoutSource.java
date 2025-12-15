package com.fpt.producerworkbench.common;

/**
 * Nguồn tiền giải ngân (cho thuế)
 */
public enum PayoutSource {
    /**
     * Thanh toán milestone
     */
    MILESTONE_PAYMENT,
    
    /**
     * Đền bù khi chấm dứt hợp đồng
     */
    TERMINATION_COMPENSATION,
    
    /**
     * Hoàn thuế
     */
    TAX_REFUND,
    
    /**
     * Thu nhập khác
     */
    OTHER_INCOME
}


