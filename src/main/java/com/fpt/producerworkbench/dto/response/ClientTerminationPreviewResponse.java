package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response preview khi CLIENT chấm dứt hợp đồng
 * Chỉ hiển thị thông tin quan trọng cho Client:
 * - Họ nhận lại được bao nhiêu
 * - Đền bù bao nhiêu
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientTerminationPreviewResponse {
    
    private BigDecimal totalAmount; // Tổng giá trị hợp đồng
    private BigDecimal compensationAmount; // Số tiền đền bù (để Client biết mất bao nhiêu)
    private BigDecimal clientWillReceive; // Số tiền Client được hoàn lại
    
    private String warning; // Cảnh báo (nếu có)
}

