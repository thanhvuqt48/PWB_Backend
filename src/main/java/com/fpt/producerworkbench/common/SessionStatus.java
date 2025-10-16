package com.fpt.producerworkbench.common;

public enum SessionStatus {
    SCHEDULED,   // Đã lên lịch, chưa bắt đầu
    ACTIVE,      // Đang diễn ra
    PAUSED,      // Tạm dừng
    ENDED,       // Đã kết thúc
    CANCELLED    // Đã hủy
}