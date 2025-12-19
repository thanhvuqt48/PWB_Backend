package com.fpt.producerworkbench.common;

/**
 * Trạng thái thanh toán
 */
public enum PaymentStatus {
    PENDING,      // Đang chờ thanh toán
    PROCESSING,   // Đang xử lý
    COMPLETED,    // Đã hoàn thành
    FAILED,       // Thất bại
    EXPIRED       // Hết hạn
}

