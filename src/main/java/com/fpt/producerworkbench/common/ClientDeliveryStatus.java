package com.fpt.producerworkbench.common;

public enum ClientDeliveryStatus {
    DELIVERED,      // Đã gửi cho client
    REJECTED,       // Client từ chối
    REQUEST_EDIT,   // Client yêu cầu chỉnh sửa
    ACCEPTED        // Client chấp nhận (đã OK để bàn giao)
}

