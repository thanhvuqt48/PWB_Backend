package com.fpt.producerworkbench.common;

/**
 * Loại giao dịch balance
 */
public enum TransactionType {
    // === TERMINATION RELATED ===
    /**
     * Team Members nhận đền bù khi chấm dứt
     */
    TERMINATION_TEAM_COMPENSATION,
    
    /**
     * Owner nhận đền bù khi chấm dứt (Client chấm dứt)
     */
    TERMINATION_OWNER_COMPENSATION,
    
    /**
     * Client nhận hoàn tiền khi chấm dứt
     */
    TERMINATION_CLIENT_REFUND,
    
    /**
     * Owner đền bù cho Team (Owner chấm dứt)
     */
    OWNER_COMPENSATE_TEAM,
    
    /**
     * Hoàn thuế (lần 2 sau ngày 20)
     */
    TAX_REFUND,
    
    // === WITHDRAWAL RELATED ===
    /**
     * Rút tiền từ balance
     */
    WITHDRAWAL,
    
    // === PAYMENT RELATED ===
    /**
     * Thanh toán hợp đồng
     */
    PAYMENT,
    
    /**
     * Thanh toán milestone
     */
    MILESTONE_PAYMENT,
    
    /**
     * Hoàn tiền
     */
    REFUND,
    
    // === OTHER ===
    /**
     * Nạp tiền
     */
    DEPOSIT,
    
    /**
     * Điều chỉnh
     */
    ADJUSTMENT,
    
    /**
     * Thanh toán subscription
     */
    SUBSCRIPTION
}


