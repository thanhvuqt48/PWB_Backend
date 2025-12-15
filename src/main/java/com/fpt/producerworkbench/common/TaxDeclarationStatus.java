package com.fpt.producerworkbench.common;

/**
 * Trạng thái tờ khai thuế
 */
public enum TaxDeclarationStatus {
    /**
     * Nháp
     */
    DRAFT,
    
    /**
     * Đã hoàn thiện
     */
    FINALIZED,
    
    /**
     * Đã nộp
     */
    SUBMITTED,
    
    /**
     * Đã được chấp nhận
     */
    ACCEPTED,
    
    /**
     * Bị từ chối
     */
    REJECTED,
    
    /**
     * Đã bổ sung
     */
    AMENDED
}


