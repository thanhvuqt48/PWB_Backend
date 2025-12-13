package com.fpt.producerworkbench.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response preview khi OWNER chấm dứt hợp đồng
 * Chỉ hiển thị thông tin quan trọng cho Owner:
 * - Số tiền phải chuyển (gross, không trừ thuế)
 * - Danh sách thành viên nhận đền bù (chi tiết từng người, cột mốc, vai trò)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerTerminationPreviewResponse {
    
    private BigDecimal totalAmount; // Tổng giá trị hợp đồng
    private BigDecimal totalTeamCompensation; // Tổng đền bù cho Team (gross, chưa trừ thuế)
    private BigDecimal clientWillReceive; // Số tiền Client được hoàn lại (100%)
    
    private List<TeamMemberCompensationInfo> teamMembers; // Danh sách thành viên nhận đền bù (chi tiết)
    private String warning; // Cảnh báo (nếu có)
}

