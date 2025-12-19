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
    
    private BigDecimal ownerWillReceive; // Số tiền Owner sẽ nhận được (trước thuế, gross)
    private BigDecimal requiredPaymentAmount; // Số tiền Owner phải chuyển từ túi (gross)
    private BigDecimal currentOwnerBalance; // Số dư hiện tại của Owner (để reference)
    private Boolean ownerHasSufficientBalance; // Owner có đủ tiền trong balance không
    
    private Boolean hasTwoPayments; // Có thanh toán 2 lần không (sau ngày 20)
    
    private List<TeamMemberCompensationInfo> teamMembers; // Danh sách thành viên nhận đền bù (chi tiết)
    private String warning; // Cảnh báo (nếu có)
}

