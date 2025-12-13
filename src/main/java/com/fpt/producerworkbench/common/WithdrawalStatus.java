package com.fpt.producerworkbench.common;

public enum WithdrawalStatus {
    PENDING,      // Đang chờ duyệt
    APPROVED,     // Đã duyệt, đang xử lý
    REJECTED,     // Từ chối
    COMPLETED,    // Hoàn thành (đã chuyển tiền)
    FAILED        // Thất bại
}

