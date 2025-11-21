package com.fpt.producerworkbench.common;

/**
 * Enum để quản lý trạng thái của comment trên track
 */
public enum CommentStatus {
    /**
     * Chưa xử lý - trạng thái mặc định khi comment mới được tạo
     */
    PENDING,
    
    /**
     * Đang xử lý - track owner đang xử lý feedback này
     */
    IN_PROGRESS,
    
    /**
     * Đã xử lý - track owner đã hoàn thành xử lý feedback
     */
    RESOLVED
}



