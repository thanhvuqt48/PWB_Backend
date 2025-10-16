package com.fpt.producerworkbench.common;

public enum InvitationStatus {
    PENDING,    // Đang chờ phản hồi
    ACCEPTED,   // Đã chấp nhận
    DECLINED,   // Đã từ chối
    CANCELLED,  // Đã bị hủy (bởi host)
    EXPIRED     // Đã hết hạn (optional)
}