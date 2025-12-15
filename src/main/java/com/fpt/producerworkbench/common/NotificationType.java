package com.fpt.producerworkbench.common;

public enum NotificationType {
    SYSTEM,                    // Thông báo từ hệ thống
    PROJECT_INVITATION,        // Lời mời tham gia dự án
    MILESTONE_INVITATION,      // Lời mời tham gia milestone
    MONEY_SPLIT_REQUEST,       // Lời mời chấp nhận chia tiền trong milestone
    CONTRACT_SIGNING,          // Thông báo ký hợp đồng
    REVIEW_RECEIVED,           // Nhận đánh giá mới
    REVIEW_UPDATED,            // Đánh giá được cập nhật
    REVIEW_DELETED             // Đánh giá bị xóa
}

